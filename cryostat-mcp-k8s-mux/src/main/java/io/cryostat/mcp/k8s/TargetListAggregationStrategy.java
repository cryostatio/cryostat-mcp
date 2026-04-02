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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.cryostat.mcp.model.graphql.DiscoveryNode;
import io.cryostat.mcp.model.graphql.Target;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Aggregation strategy for merging target lists from multiple Cryostat instances. Concatenates all
 * results, adds instance metadata for traceability, and sorts by instance namespace then node name.
 * Numeric IDs are nullified to avoid collision confusion since the mux interface uses <namespace,
 * podName> semantics. Assumes no namespace overlap between instances (user configuration).
 */
@ApplicationScoped
public class TargetListAggregationStrategy implements AggregationStrategy<List<DiscoveryNode>> {

    private static final String INSTANCE_LABEL_KEY = "cryostat.instance.namespace";

    @Inject Logger log;

    /**
     * Aggregate target lists from multiple Cryostat instances into a single unified list.
     *
     * @param results List of target lists from each instance (may contain nulls for failed
     *     instances)
     * @param instances List of instances that were queried
     * @return Merged and sorted list of discovery nodes
     * @throws Exception if aggregation fails
     */
    @Override
    public List<DiscoveryNode> aggregate(
            List<List<DiscoveryNode>> results, List<CryostatInstance> instances) throws Exception {
        if (results == null || results.isEmpty()) {
            log.warn("No target lists to aggregate, returning empty list");
            return List.of();
        }

        List<DiscoveryNode> allNodes = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            List<DiscoveryNode> result = results.get(i);
            if (result != null && !result.isEmpty()) {
                CryostatInstance instance = instances.get(i);
                List<DiscoveryNode> nodesWithMetadata =
                        result.stream()
                                .map(node -> addInstanceMetadata(node, instance.namespace()))
                                .collect(Collectors.toList());
                allNodes.addAll(nodesWithMetadata);
            }
        }

        if (allNodes.isEmpty()) {
            log.warn("All target list queries returned empty or failed, returning empty list");
            return List.of();
        }

        log.infof("Aggregating %d targets from %d instances", allNodes.size(), instances.size());

        checkForDuplicates(allNodes);

        List<DiscoveryNode> processedNodes =
                allNodes.stream()
                        .map(this::nullifyIds)
                        .sorted(
                                Comparator.comparing(
                                                (DiscoveryNode node) ->
                                                        node.labels()
                                                                .getOrDefault(
                                                                        INSTANCE_LABEL_KEY, ""))
                                        .thenComparing(DiscoveryNode::name))
                        .collect(Collectors.toList());

        log.infof("Merged target list contains %d nodes", processedNodes.size());
        return processedNodes;
    }

    /**
     * Add instance metadata label to a discovery node.
     *
     * @param node The discovery node
     * @param instanceNamespace The namespace of the Cryostat instance
     * @return Node with added metadata
     */
    private DiscoveryNode addInstanceMetadata(DiscoveryNode node, String instanceNamespace) {
        Map<String, String> updatedLabels = new HashMap<>(node.labels());
        updatedLabels.put(INSTANCE_LABEL_KEY, instanceNamespace);

        return new DiscoveryNode(
                node.id(), node.name(), node.nodeType(), updatedLabels, node.target());
    }

    /**
     * Nullify IDs in a discovery node and its target. This prevents ID collision confusion since
     * different Cryostat instances may assign the same IDs to different entities. The mux interface
     * uses <namespace, podName> semantics, so numeric IDs are not needed.
     *
     * @param node The node to process
     * @return Node with null IDs
     */
    private DiscoveryNode nullifyIds(DiscoveryNode node) {
        if (node == null) {
            return null;
        }

        Target nullifiedTarget = null;
        if (node.target() != null) {
            nullifiedTarget = nullifyTargetId(node.target());
        }

        return new DiscoveryNode(
                null, node.name(), node.nodeType(), node.labels(), nullifiedTarget);
    }

    /**
     * Nullify the ID in a Target.
     *
     * @param target The target to process
     * @return Target with null ID
     */
    private Target nullifyTargetId(Target target) {
        if (target == null) {
            return null;
        }

        return new Target(
                null,
                target.connectUrl(),
                target.alias(),
                target.jvmId(),
                target.labels(),
                target.annotations());
    }

    /**
     * Check for duplicate targets by jvmId. Duplicates indicate misconfiguration (overlapping
     * targetNamespaces between instances). Log a warning if found.
     *
     * @param nodes List of nodes to check
     */
    private void checkForDuplicates(List<DiscoveryNode> nodes) {
        Map<String, List<DiscoveryNode>> byJvmId = new HashMap<>();

        for (DiscoveryNode node : nodes) {
            if (node.target() != null && node.target().jvmId() != null) {
                String jvmId = node.target().jvmId();
                byJvmId.computeIfAbsent(jvmId, k -> new ArrayList<>()).add(node);
            }
        }

        for (Map.Entry<String, List<DiscoveryNode>> entry : byJvmId.entrySet()) {
            if (entry.getValue().size() > 1) {
                String jvmId = entry.getKey();
                List<String> nodeNames =
                        entry.getValue().stream()
                                .map(DiscoveryNode::name)
                                .collect(Collectors.toList());
                log.warnf(
                        "Duplicate target found with jvmId '%s' in nodes: %s. "
                                + "This indicates misconfiguration - Cryostat instances should "
                                + "monitor distinct targetNamespaces. Using first occurrence.",
                        jvmId, nodeNames);
            }
        }
    }
}
