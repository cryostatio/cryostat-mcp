/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.mcp.k8s;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.cryostat.mcp.CryostatMCP;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Multi-tenant MCP server that wraps CryostatMCP tools with namespace-based routing. Uses
 * reflection to discover tools from CryostatMCP and programmatically adds namespace parameters.
 */
@ApplicationScoped
public class K8sMultiMCP {

    private static final Logger LOG = Logger.getLogger(K8sMultiMCP.class);

    @Inject ToolManager toolManager;
    @Inject CryostatMCPInstanceManager instanceManager;
    @Inject CryostatInstanceDiscovery discovery;
    @Inject ObjectMapper objectMapper;
    @Inject K8sMultiMCPConfig config;

    private Set<String> namespaceRequiredTools;
    private Set<String> namespaceOptionalTools;

    void onStart(@Observes StartupEvent event) {
        namespaceRequiredTools = config.namespaceRequiredTools();
        namespaceOptionalTools = config.namespaceOptionalTools();
        LOG.info("Registering multi-tenant MCP tools using reflection");
        registerToolsFromCryostatMCP();
    }

    private void registerToolsFromCryostatMCP() {
        Method[] methods = CryostatMCP.class.getDeclaredMethods();

        for (Method method : methods) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            if (toolAnnotation == null) {
                continue;
            }

            String toolName = method.getName();
            if (!namespaceRequiredTools.contains(toolName)
                    && !namespaceOptionalTools.contains(toolName)) {
                LOG.debugf("Skipping tool '%s' - not in allowlist", toolName);
                continue;
            }

            registerTool(method, toolAnnotation, toolName);
        }

        LOG.infof(
                "Registered %d multi-tenant tools",
                namespaceRequiredTools.size() + namespaceOptionalTools.size());
    }

    private void registerTool(Method method, Tool toolAnnotation, String toolName) {
        boolean namespaceRequired = namespaceRequiredTools.contains(toolName);

        String description = buildToolDescription(toolAnnotation.description(), namespaceRequired);

        var toolBuilder = toolManager.newTool(toolName).setDescription(description);

        String namespaceDesc =
                namespaceRequired
                        ? "The namespace where the target application is running."
                        : "The namespace of the Cryostat instance to query. If not provided,"
                                + " queries the first available instance.";
        toolBuilder.addArgument("namespace", namespaceDesc, namespaceRequired, String.class);

        // Add original method parameters
        Parameter[] parameters = method.getParameters();
        for (Parameter param : parameters) {
            ToolArg argAnnotation = param.getAnnotation(ToolArg.class);
            if (argAnnotation == null) {
                continue;
            }

            String paramName = param.getName();
            Class<?> paramType = getArgumentType(param.getType());
            toolBuilder.addArgument(
                    paramName, argAnnotation.description(), argAnnotation.required(), paramType);
        }

        // Set the handler that routes to the appropriate instance
        toolBuilder.setHandler(
                toolArgs -> {
                    try {
                        Object result =
                                invokeTool(method, toolArgs.args(), namespaceRequired, null);
                        // Serialize result to JSON string for ToolResponse
                        String jsonResult =
                                result instanceof String
                                        ? (String) result
                                        : objectMapper.writeValueAsString(result);
                        return ToolResponse.success(jsonResult);
                    } catch (Exception e) {
                        LOG.errorf(e, "Failed to invoke tool '%s'", toolName);

                        // Unwrap InvocationTargetException to get the real cause
                        Throwable cause = e;
                        if (e instanceof java.lang.reflect.InvocationTargetException) {
                            cause = e.getCause() != null ? e.getCause() : e;
                        }

                        String errorMsg = cause.getMessage();
                        if (errorMsg == null || errorMsg.isEmpty()) {
                            errorMsg = cause.getClass().getSimpleName() + ": " + cause.toString();
                        }
                        return ToolResponse.error("Tool invocation failed: " + errorMsg);
                    }
                });

        toolBuilder.register();

        LOG.debugf("Registered tool '%s' with namespace parameter", toolName);
    }

    private String buildToolDescription(String originalDescription, boolean namespaceRequired) {
        String namespaceInfo =
                namespaceRequired
                        ? " Namespace parameter is required to identify the Cryostat instance"
                                + " managing the target."
                        : " If namespace is provided, routes to that specific Cryostat instance;"
                                + " otherwise aggregates results from all available instances.";

        return originalDescription + namespaceInfo;
    }

    private Class<?> getArgumentType(Class<?> javaType) {
        // Map Java types to MCP argument types
        if (javaType == String.class) {
            return String.class;
        } else if (javaType == long.class
                || javaType == Long.class
                || javaType == int.class
                || javaType == Integer.class
                || javaType == double.class
                || javaType == Double.class) {
            return Number.class;
        } else if (javaType == boolean.class || javaType == Boolean.class) {
            return Boolean.class;
        } else if (List.class.isAssignableFrom(javaType)) {
            return List.class;
        } else {
            return Object.class;
        }
    }

    private Object invokeTool(
            Method method,
            java.util.Map<String, Object> args,
            boolean namespaceRequired,
            String authorizationHeader)
            throws Exception {
        String namespace = (String) args.get("namespace");

        if ((namespace == null || namespace.isEmpty()) && !namespaceRequired) {
            return aggregateResults(method, args, authorizationHeader);
        }

        if (namespace == null || namespace.isEmpty()) {
            throw new IllegalArgumentException("Namespace is required for this operation");
        }

        CryostatMCP mcp = instanceManager.createInstance(namespace, authorizationHeader);
        Object[] methodArgs = prepareMethodArguments(method, args);
        try {
            return method.invoke(mcp, methodArgs);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();

            // Handle WebApplicationException (HTTP errors)
            if (cause instanceof jakarta.ws.rs.WebApplicationException) {
                jakarta.ws.rs.WebApplicationException webEx =
                        (jakarta.ws.rs.WebApplicationException) cause;
                String errorDetail =
                        String.format(
                                "HTTP %d: %s",
                                webEx.getResponse().getStatus(),
                                webEx.getResponse().getStatusInfo().getReasonPhrase());
                throw new Exception(
                        "Request to Cryostat in namespace '"
                                + namespace
                                + "' failed: "
                                + errorDetail,
                        cause);
            }

            // Handle ProcessingException (connection errors, etc.)
            if (cause instanceof jakarta.ws.rs.ProcessingException) {
                String errorMsg = cause.getMessage();
                Throwable rootCause = cause.getCause();
                if (rootCause != null) {
                    errorMsg = rootCause.getClass().getSimpleName() + ": " + rootCause.getMessage();
                }
                throw new Exception(
                        "Failed to connect to Cryostat in namespace '"
                                + namespace
                                + "': "
                                + errorMsg,
                        cause);
            }

            // Handle RuntimeException (may contain nested causes)
            if (cause instanceof RuntimeException) {
                Throwable rootCause = cause;
                while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
                    rootCause = rootCause.getCause();
                }

                String errorMsg = cause.getMessage();
                if (rootCause != cause) {
                    errorMsg += " (root cause: " + rootCause.getClass().getSimpleName();
                    if (rootCause.getMessage() != null) {
                        errorMsg += ": " + rootCause.getMessage();
                    }
                    errorMsg += ")";
                }

                throw new Exception(
                        "Error invoking Cryostat method in namespace '"
                                + namespace
                                + "': "
                                + errorMsg,
                        cause);
            }

            // Re-throw with cause details
            throw new Exception(
                    "Error invoking Cryostat method in namespace '"
                            + namespace
                            + "': "
                            + cause.getClass().getSimpleName()
                            + ": "
                            + cause.getMessage(),
                    cause);
        }
    }

    private Object aggregateResults(
            Method method, java.util.Map<String, Object> args, String authorizationHeader)
            throws Exception {
        List<CryostatInstance> instances = new ArrayList<>(discovery.getAllInstances());
        if (instances.isEmpty()) {
            throw new IllegalStateException("No Cryostat instances available");
        }

        String methodName = method.getName();
        LOG.infof(
                "Aggregating results from %d instances for tool '%s'",
                instances.size(), methodName);

        // Currently only scrapeMetrics supports aggregation
        if ("scrapeMetrics".equals(methodName)) {
            return aggregateMetrics(method, args, instances, authorizationHeader);
        }

        throw new UnsupportedOperationException(
                "Aggregation not supported for tool: " + methodName);
    }

    private String aggregateMetrics(
            Method method,
            java.util.Map<String, Object> args,
            List<CryostatInstance> instances,
            String authorizationHeader)
            throws Exception {
        List<String> allMetrics = new ArrayList<>();

        for (CryostatInstance instance : instances) {
            try {
                CryostatMCP mcp =
                        instanceManager.createInstance(instance.namespace(), authorizationHeader);
                Object[] methodArgs = prepareMethodArguments(method, args);
                String metrics = (String) method.invoke(mcp, methodArgs);

                if (metrics != null && !metrics.isEmpty()) {
                    allMetrics.add(metrics);
                }
            } catch (Exception e) {
                LOG.warnf(
                        e,
                        "Failed to scrape metrics from instance '%s' in namespace '%s'",
                        instance.name(),
                        instance.namespace());
                // Continue with other instances
            }
        }

        if (allMetrics.isEmpty()) {
            return "";
        }

        // Concatenate, sort, and deduplicate metrics
        return allMetrics.stream()
                .flatMap(metrics -> Arrays.stream(metrics.split("\n")))
                .filter(line -> !line.isEmpty())
                .sorted()
                .distinct()
                .collect(Collectors.joining("\n"));
    }

    private Object[] prepareMethodArguments(Method method, java.util.Map<String, Object> args) {
        Parameter[] parameters = method.getParameters();
        Object[] methodArgs = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            String paramName = param.getName();
            Object value = args.get(paramName);

            if (value instanceof Number) {
                Class<?> paramType = param.getType();
                Number numValue = (Number) value;
                if (paramType == long.class || paramType == Long.class) {
                    value = numValue.longValue();
                } else if (paramType == int.class || paramType == Integer.class) {
                    value = numValue.intValue();
                } else if (paramType == double.class || paramType == Double.class) {
                    value = numValue.doubleValue();
                }
            }

            methodArgs[i] = value;
        }

        return methodArgs;
    }
}
