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
import io.cryostat.mcp.model.DiscoveryNode;

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

    @Inject Logger log;
    @Inject CryostatMCPInstanceManager instanceManager;
    @Inject CryostatInstanceDiscovery discovery;
    @Inject PrometheusMetricsAggregationStrategy prometheusAggregationStrategy;
    @Inject DiscoveryTreeAggregationStrategy discoveryTreeAggregationStrategy;
    @Inject TargetListAggregationStrategy targetListAggregationStrategy;

    @Tool(
            description =
                    "Get the full discovery tree from all Cryostat instances. This returns a"
                        + " unified tree with Universe as root, containing Realm nodes from all"
                        + " instances. Each Realm node is labeled with its source instance"
                        + " namespace for traceability. The tree structure is: Universe -> Realm ->"
                        + " Namespace -> Deployment -> ReplicaSet -> Pod -> Target.")
    public DiscoveryNode getGlobalDiscoveryTree() {
        return aggregateFromAllInstances(
                mcp -> mcp.getDiscoveryTree(true), discoveryTreeAggregationStrategy);
    }

    @Tool(
            description =
                    "Get a list of all discovered Target applications from all Cryostat instances."
                        + " Each Target belongs to a Discovery Node (typically a Pod). Results are"
                        + " labeled with the source instance namespace for traceability. If no"
                        + " filter inputs are provided, the full list of all discovered Targets"
                        + " will be returned. Otherwise, if any filter inputs are provided, then"
                        + " only Targets which match all of the given inputs will be returned.")
    public List<io.cryostat.mcp.model.graphql.DiscoveryNode> listGlobalTargets(
            @ToolArg(
                            description =
                                    "List of Discovery Node names to match. Discovery Nodes"
                                            + " matching any of the given names will be selected.")
                    List<String> names,
            @ToolArg(
                            description =
                                    "List of Discovery Node label selectors to match. Discovery"
                                        + " Nodes matching any of the given label selectors will be"
                                        + " selected. Label selectors use the Kubernetes selector"
                                        + " syntax: \"my-label=foo\", \"my-label != bar\", \"env in"
                                        + " (prod, stage)\", \"!present\".")
                    List<String> labels,
            @ToolArg(
                            description =
                                    "Query historical targets from audit log. This is more"
                                        + " expensive and should only be used when historical data"
                                        + " is needed.")
                    Boolean useAuditLog) {
        return aggregateFromAllInstances(
                mcp -> mcp.listTargets(null, null, names, labels, useAuditLog),
                targetListAggregationStrategy);
    }

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
            log.warn("No Cryostat instances available for non-directed tool invocation");
            try {
                return aggregationStrategy.aggregate(List.of(), instances);
            } catch (Exception e) {
                throw new RuntimeException("Failed to aggregate empty results", e);
            }
        }

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
                                                            log.warnf(
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
