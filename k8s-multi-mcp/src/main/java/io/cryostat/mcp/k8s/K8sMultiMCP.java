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
import java.util.List;

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
 * Multi-tenant MCP server that wraps CryostatMCP tools with namespace-based routing.
 *
 * <p>This server provides three types of tools:
 *
 * <ul>
 *   <li><b>Directed tools:</b> All CryostatMCP tools are registered as directed tools, requiring a
 *       namespace parameter to route requests to a specific Cryostat instance.
 *   <li><b>Non-directed tools:</b> Custom tools that query all available Cryostat instances and
 *       aggregate results using configurable aggregation strategies.
 *   <li><b>System tools:</b> Tools that operate on the multi-MCP system itself, such as listing
 *       discovered Cryostat instances, without calling any underlying MCP instances.
 * </ul>
 */
@ApplicationScoped
public class K8sMultiMCP {

    private static final Logger LOG = Logger.getLogger(K8sMultiMCP.class);

    @Inject ToolManager toolManager;
    @Inject CryostatMCPInstanceManager instanceManager;
    @Inject CryostatInstanceDiscovery discovery;
    @Inject ObjectMapper objectMapper;

    void onStart(@Observes StartupEvent event) {
        LOG.info("Registering multi-tenant MCP tools");
        registerToolsFromCryostatMCP();
        registerNonDirectedTools();
        registerSystemTools();
    }

    /**
     * Registers all CryostatMCP tools as directed tools with required namespace parameter. Uses
     * reflection to discover tools and programmatically adds namespace routing.
     */
    private void registerToolsFromCryostatMCP() {
        Method[] methods = CryostatMCP.class.getDeclaredMethods();
        int registeredCount = 0;

        for (Method method : methods) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            if (toolAnnotation == null) {
                continue;
            }

            String toolName = method.getName();
            registerDirectedTool(method, toolAnnotation, toolName);
            registeredCount++;
        }

        LOG.infof("Registered %d directed tools from CryostatMCP", registeredCount);
    }

    /**
     * Registers non-directed tools that operate across all Cryostat instances. These tools query
     * all available instances and aggregate results.
     */
    private void registerNonDirectedTools() {
        // Register scrapeGlobalMetrics tool
        NonDirectedToolDescriptor<String> scrapeGlobalMetrics =
                NonDirectedToolDescriptor.<String>builder()
                        .name("scrapeGlobalMetrics")
                        .description(
                                "Scrape Prometheus metrics from all discovered Cryostat instances"
                                        + " and aggregate them. Returns metrics in Prometheus text"
                                        + " format, sorted and deduplicated.")
                        .addArgument(
                                new ToolArgumentDescriptor(
                                        "minTargetScore",
                                        "Minimum target score for filtering metrics",
                                        false,
                                        Number.class))
                        .invoker(
                                (mcp, args) -> {
                                    Object minTargetScoreObj = args.get("minTargetScore");
                                    long minTargetScore =
                                            minTargetScoreObj != null
                                                    ? ((Number) minTargetScoreObj).longValue()
                                                    : 0L;
                                    return mcp.scrapeMetrics(minTargetScore);
                                })
                        .aggregationStrategy(new PrometheusMetricsAggregationStrategy())
                        .returnType(String.class)
                        .build();

        registerNonDirectedTool(scrapeGlobalMetrics);

        LOG.info("Registered 1 non-directed tool");
    }

    /**
     * Registers system tools that operate on the multi-MCP system itself. These tools do not call
     * any underlying Cryostat MCP instances.
     */
    private void registerSystemTools() {
        // Register listCryostatInstances tool
        var toolBuilder =
                toolManager
                        .newTool("listCryostatInstances")
                        .setDescription(
                                "List all discovered Cryostat instances (services) in the"
                                        + " Kubernetes cluster. Returns information about each"
                                        + " instance including name, namespace, application URL,"
                                        + " and target namespaces being monitored.");

        toolBuilder.setHandler(
                toolArgs -> {
                    try {
                        List<CryostatInstance> instances =
                                discovery.getAllInstances().stream().toList();
                        String jsonResult = objectMapper.writeValueAsString(instances);
                        return ToolResponse.success(jsonResult);
                    } catch (Exception e) {
                        LOG.errorf(e, "Failed to list Cryostat instances");
                        String errorMsg = e.getMessage();
                        if (errorMsg == null || errorMsg.isEmpty()) {
                            errorMsg = e.getClass().getSimpleName() + ": " + e.toString();
                        }
                        return ToolResponse.error("Failed to list instances: " + errorMsg);
                    }
                });

        toolBuilder.register();

        LOG.info("Registered 1 system tool");
    }

    /**
     * Registers a directed tool with required namespace parameter.
     *
     * @param method The CryostatMCP method to wrap
     * @param toolAnnotation The @Tool annotation from the method
     * @param toolName The name of the tool
     */
    private void registerDirectedTool(Method method, Tool toolAnnotation, String toolName) {
        String description = buildDirectedToolDescription(toolAnnotation.description());

        var toolBuilder = toolManager.newTool(toolName).setDescription(description);

        // Add required namespace parameter
        toolBuilder.addArgument(
                "namespace",
                "The namespace of the Cryostat instance to query (required).",
                true,
                String.class);

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

        // Set the handler that routes to the specific namespace
        toolBuilder.setHandler(
                toolArgs -> {
                    try {
                        Object result = invokeDirectedTool(method, toolArgs.args(), null);
                        // Serialize result to JSON string for ToolResponse
                        String jsonResult =
                                result instanceof String
                                        ? (String) result
                                        : objectMapper.writeValueAsString(result);
                        return ToolResponse.success(jsonResult);
                    } catch (Exception e) {
                        LOG.errorf(e, "Failed to invoke directed tool '%s'", toolName);

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

        LOG.debugf("Registered directed tool '%s'", toolName);
    }

    /**
     * Registers a non-directed tool that queries all instances and aggregates results.
     *
     * @param descriptor The non-directed tool descriptor
     * @param <T> The return type of the tool
     */
    private <T> void registerNonDirectedTool(NonDirectedToolDescriptor<T> descriptor) {
        var toolBuilder =
                toolManager
                        .newTool(descriptor.getName())
                        .setDescription(descriptor.getDescription());

        // Add arguments from descriptor
        for (ToolArgumentDescriptor arg : descriptor.getArguments()) {
            toolBuilder.addArgument(
                    arg.getName(), arg.getDescription(), arg.isRequired(), arg.getType());
        }

        // Set handler that aggregates results from all instances
        toolBuilder.setHandler(
                toolArgs -> {
                    try {
                        T result = invokeNonDirectedTool(descriptor, toolArgs.args(), null);
                        // Serialize result to JSON string for ToolResponse
                        String jsonResult =
                                result instanceof String
                                        ? (String) result
                                        : objectMapper.writeValueAsString(result);
                        return ToolResponse.success(jsonResult);
                    } catch (Exception e) {
                        LOG.errorf(
                                e, "Failed to invoke non-directed tool '%s'", descriptor.getName());

                        String errorMsg = e.getMessage();
                        if (errorMsg == null || errorMsg.isEmpty()) {
                            errorMsg = e.getClass().getSimpleName() + ": " + e.toString();
                        }
                        return ToolResponse.error("Tool invocation failed: " + errorMsg);
                    }
                });

        toolBuilder.register();

        LOG.debugf("Registered non-directed tool '%s'", descriptor.getName());
    }

    /**
     * Builds the description for a directed tool.
     *
     * @param originalDescription The original tool description from CryostatMCP
     * @return Enhanced description explaining namespace requirement
     */
    private String buildDirectedToolDescription(String originalDescription) {
        return originalDescription
                + " Namespace parameter is required to identify the Cryostat instance managing the"
                + " target.";
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

    /**
     * Invokes a directed tool on a specific Cryostat instance identified by namespace.
     *
     * @param method The CryostatMCP method to invoke
     * @param args The tool arguments including namespace
     * @param authorizationHeader Optional authorization header
     * @return The result from the tool invocation
     * @throws Exception if invocation fails
     */
    private Object invokeDirectedTool(
            Method method, java.util.Map<String, Object> args, String authorizationHeader)
            throws Exception {
        String namespace = (String) args.get("namespace");

        if (namespace == null || namespace.isEmpty()) {
            throw new IllegalArgumentException("Namespace is required for directed tools");
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

    /**
     * Invokes a non-directed tool across all Cryostat instances and aggregates results.
     *
     * @param descriptor The non-directed tool descriptor
     * @param args The tool arguments
     * @param authorizationHeader Optional authorization header
     * @param <T> The return type of the tool
     * @return The aggregated result
     * @throws Exception if invocation or aggregation fails
     */
    private <T> T invokeNonDirectedTool(
            NonDirectedToolDescriptor<T> descriptor,
            java.util.Map<String, Object> args,
            String authorizationHeader)
            throws Exception {
        List<CryostatInstance> instances = new ArrayList<>(discovery.getAllInstances());
        if (instances.isEmpty()) {
            LOG.warn("No Cryostat instances available for non-directed tool invocation");
            // Return empty result based on type
            return descriptor.getAggregationStrategy().aggregate(List.of(), instances);
        }

        LOG.infof(
                "Invoking non-directed tool '%s' across %d instances",
                descriptor.getName(), instances.size());

        List<T> results = new ArrayList<>();

        for (CryostatInstance instance : instances) {
            try {
                CryostatMCP mcp =
                        instanceManager.createInstance(instance.namespace(), authorizationHeader);
                T result = descriptor.getInvoker().invoke(mcp, args);
                results.add(result);
            } catch (Exception e) {
                LOG.warnf(
                        e,
                        "Failed to invoke tool '%s' on instance '%s' in namespace '%s'",
                        descriptor.getName(),
                        instance.name(),
                        instance.namespace());
                // Add null to maintain alignment with instances list
                results.add(null);
            }
        }

        return descriptor.getAggregationStrategy().aggregate(results, instances);
    }

    /**
     * Prepares method arguments from the tool arguments map, handling type conversions.
     *
     * @param method The method to prepare arguments for
     * @param args The tool arguments map
     * @return Array of method arguments in the correct order and types
     */
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
