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
import java.util.stream.Collectors;

import io.cryostat.mcp.model.DiscoveryNode;
import io.cryostat.mcp.model.KeyValue;
import io.cryostat.mcp.model.Target;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Aggregation strategy for merging discovery trees from multiple Cryostat instances. Each instance
 * returns a tree with Universe as root. This strategy merges all Realm nodes (children of each
 * Universe) into a single unified Universe, adding instance metadata for traceability. Numeric IDs
 * are nullified to avoid collision confusion since the mux interface uses <namespace, podName>
 * semantics.
 */
@ApplicationScoped
public class DiscoveryTreeAggregationStrategy implements AggregationStrategy<DiscoveryNode> {

    private static final String INSTANCE_LABEL_KEY = "cryostat.instance.namespace";

    @Inject Logger log;

    /**
     * Aggregate discovery trees from multiple Cryostat instances into a single unified tree.
     *
     * @param results List of discovery trees from each instance (may contain nulls for failed
     *     instances)
     * @param instances List of instances that were queried
     * @return Merged discovery tree with unified Universe root
     * @throws Exception if aggregation fails
     */
    @Override
    public DiscoveryNode aggregate(List<DiscoveryNode> results, List<CryostatInstance> instances)
            throws Exception {
        if (results == null || results.isEmpty()) {
            log.warn("No discovery trees to aggregate, returning empty Universe");
            return createEmptyUniverse();
        }

        List<DiscoveryNode> validTrees =
                results.stream().filter(tree -> tree != null).collect(Collectors.toList());

        if (validTrees.isEmpty()) {
            log.warn("All discovery tree queries failed, returning empty Universe");
            return createEmptyUniverse();
        }

        log.infof(
                "Aggregating %d discovery trees from %d instances",
                validTrees.size(), instances.size());

        // Collect all Realm nodes from each instance's Universe
        List<DiscoveryNode> allRealms = new ArrayList<>();
        for (int i = 0; i < validTrees.size(); i++) {
            DiscoveryNode tree = validTrees.get(i);
            CryostatInstance instance = instances.get(i);

            if (tree.children() != null && !tree.children().isEmpty()) {
                List<DiscoveryNode> realmsWithMetadata =
                        tree.children().stream()
                                .map(realm -> addInstanceMetadata(realm, instance.namespace()))
                                .collect(Collectors.toList());
                allRealms.addAll(realmsWithMetadata);
            }
        }

        // Create unified Universe with all Realms as children
        DiscoveryNode mergedUniverse =
                new DiscoveryNode(
                        null,
                        "Universe",
                        "Universe",
                        List.of(),
                        allRealms.stream()
                                .map(this::nullifyIdsRecursively)
                                .collect(Collectors.toList()),
                        null);

        log.infof("Merged discovery tree contains %d Realm nodes", allRealms.size());
        return mergedUniverse;
    }

    /**
     * Create an empty Universe node (no children).
     *
     * @return Empty Universe discovery node
     */
    private DiscoveryNode createEmptyUniverse() {
        return new DiscoveryNode(null, "Universe", "Universe", List.of(), List.of(), null);
    }

    /**
     * Add instance metadata label to a Realm node.
     *
     * @param realm The Realm node
     * @param instanceNamespace The namespace of the Cryostat instance
     * @return Realm node with added metadata
     */
    private DiscoveryNode addInstanceMetadata(DiscoveryNode realm, String instanceNamespace) {
        List<KeyValue> updatedLabels = new ArrayList<>(realm.labels());
        updatedLabels.add(new KeyValue(INSTANCE_LABEL_KEY, instanceNamespace));

        return new DiscoveryNode(
                realm.id(),
                realm.name(),
                realm.nodeType(),
                updatedLabels,
                realm.children(),
                realm.target());
    }

    /**
     * Recursively nullify all IDs in a discovery node tree. This prevents ID collision confusion
     * since different Cryostat instances may assign the same IDs to different entities. The mux
     * interface uses <namespace, podName> semantics, so numeric IDs are not needed.
     *
     * @param node The node to process
     * @return Node with all IDs set to null
     */
    private DiscoveryNode nullifyIdsRecursively(DiscoveryNode node) {
        if (node == null) {
            return null;
        }

        List<DiscoveryNode> nullifiedChildren = null;
        if (node.children() != null) {
            nullifiedChildren =
                    node.children().stream()
                            .map(this::nullifyIdsRecursively)
                            .collect(Collectors.toList());
        }

        Target nullifiedTarget = null;
        if (node.target() != null) {
            nullifiedTarget = nullifyTargetId(node.target());
        }

        return new DiscoveryNode(
                null,
                node.name(),
                node.nodeType(),
                node.labels(),
                nullifiedChildren,
                nullifiedTarget);
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
}
