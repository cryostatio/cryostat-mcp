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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.cryostat.mcp.CryostatMCP;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Non-directed tools that query all available Cryostat instances and aggregate results using
 * configurable aggregation strategies.
 */
@ApplicationScoped
public class NonDirectedTools {

    private static final Logger LOG = Logger.getLogger(NonDirectedTools.class);

    @Inject ToolManager toolManager;
    @Inject CryostatMCPInstanceManager instanceManager;
    @Inject CryostatInstanceDiscovery discovery;
    @Inject ObjectMapper objectMapper;

    void onStart(@Observes StartupEvent event) {
        LOG.info("Registering non-directed MCP tools");
        registerNonDirectedTools();
    }

    /** Registers non-directed tools that operate across all Cryostat instances. */
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
                                    double minTargetScore =
                                            minTargetScoreObj != null
                                                    ? ((Number) minTargetScoreObj).doubleValue()
                                                    : 0.0;
                                    return mcp.scrapeMetrics(minTargetScore);
                                })
                        .aggregationStrategy(new PrometheusMetricsAggregationStrategy())
                        .returnType(String.class)
                        .build();

        registerNonDirectedTool(scrapeGlobalMetrics);

        LOG.info("Registered 1 non-directed tool");
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
            Map<String, Object> args,
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
}