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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Function;

import io.cryostat.mcp.CryostatMCP;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Non-directed tools that query all available Cryostat instances and aggregate results using
 * configurable aggregation strategies.
 */
@ApplicationScoped
public class NonDirectedTools {

    private static final Logger LOG = Logger.getLogger(NonDirectedTools.class);

    @Inject CryostatMCPInstanceManager instanceManager;
    @Inject CryostatInstanceDiscovery discovery;
    @Inject PrometheusMetricsAggregationStrategy prometheusAggregationStrategy;

    @Tool(
            description =
                    "Scrape Prometheus metrics from all discovered Cryostat instances and aggregate"
                            + " them. Returns metrics in Prometheus text format, sorted and"
                            + " deduplicated.")
    public String scrapeGlobalMetrics(
            @ToolArg(description = "Minimum target score for filtering metrics")
                    Double minTargetScore) {
        double score = minTargetScore != null ? minTargetScore : 0.0;
        return aggregateFromAllInstances(
                mcp -> mcp.scrapeMetrics(score), prometheusAggregationStrategy);
    }

    /**
     * Helper method to invoke a function across all Cryostat instances and aggregate results.
     *
     * @param invoker Function to invoke on each MCP instance
     * @param aggregationStrategy Strategy to aggregate results
     * @param <T> The return type
     * @return The aggregated result
     */
    private <T> T aggregateFromAllInstances(
            Function<CryostatMCP, T> invoker, AggregationStrategy<T> aggregationStrategy) {
        List<CryostatInstance> instances = new ArrayList<>(discovery.getAllInstances());
        if (instances.isEmpty()) {
            LOG.warn("No Cryostat instances available for non-directed tool invocation");
            try {
                return aggregationStrategy.aggregate(List.of(), instances);
            } catch (Exception e) {
                throw new RuntimeException("Failed to aggregate empty results", e);
            }
        }

        LOG.debugf("Invoking non-directed tool across %d instances", instances.size());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<T>> futures =
                    instances.stream()
                            .map(
                                    instance ->
                                            CompletableFuture.supplyAsync(
                                                    () -> {
                                                        try {
                                                            CryostatMCP mcp =
                                                                    instanceManager.createInstance(
                                                                            instance.namespace());
                                                            return invoker.apply(mcp);
                                                        } catch (Exception e) {
                                                            LOG.warnf(
                                                                    e,
                                                                    "Failed to invoke tool on"
                                                                            + " instance '%s' in"
                                                                            + " namespace '%s'",
                                                                    instance.name(),
                                                                    instance.namespace());
                                                            return null;
                                                        }
                                                    },
                                                    executor))
                            .toList();

            List<T> results = futures.stream().map(CompletableFuture::join).toList();
            try {
                return aggregationStrategy.aggregate(results, instances);
            } catch (Exception e) {
                throw new RuntimeException("Failed to aggregate results", e);
            }
        }
    }
}
