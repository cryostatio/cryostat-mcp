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
package io.cryostat;

import java.util.List;
import java.util.Objects;

import io.cryostat.mcp.CryostatMCP;
import io.cryostat.mcp.k8s.CryostatMCPInstanceManager;
import io.cryostat.mcp.model.graphql.DescendantTarget;
import io.cryostat.mcp.model.graphql.DiscoveryNode;
import io.cryostat.mcp.model.graphql.Target;

import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Maps Kubernetes pod names to Cryostat target identifiers (JVM ID and target ID) using GraphQL
 * queries against Cryostat's API. This class integrates with CryostatMCPInstanceManager to reuse
 * cached GraphQL clients and authorization infrastructure. Results are cached for 5 minutes after
 * last access to reduce load on Cryostat API.
 */
@ApplicationScoped
public class CryostatPodNameMapper {

    private final CryostatMCPInstanceManager instanceManager;

    @Inject
    public CryostatPodNameMapper(CryostatMCPInstanceManager instanceManager) {
        this.instanceManager = Objects.requireNonNull(instanceManager);
    }

    CryostatPodNameMapper() {
        this.instanceManager = null;
    }

    /**
     * Gets the JVM ID for the specified pod name.
     *
     * @param namespace the Kubernetes namespace
     * @param podName the name of the pod
     * @return the JVM ID of the target running in the pod
     * @throws IllegalArgumentException if the pod is not found or has no targets
     */
    public String getJvmId(String namespace, String podName) {
        return queryPodTarget(namespace, podName).jvmId();
    }

    /**
     * Gets the target ID for the specified pod name.
     *
     * @param namespace the Kubernetes namespace
     * @param podName the name of the pod
     * @return the target ID of the target running in the pod
     * @throws IllegalArgumentException if the pod is not found or has no targets
     */
    public long getTargetId(String namespace, String podName) {
        return queryPodTarget(namespace, podName).targetId();
    }

    @CacheResult(cacheName = "pod-target-mapping")
    TargetInfo queryPodTarget(String namespace, String podName) {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(podName, "podName must not be null");

        try {
            CryostatMCP mcp = instanceManager.createInstance(namespace);

            List<DiscoveryNode> environmentNodes =
                    mcp.listEnvironmentNodes(List.of("Pod"), List.of(podName));

            if (environmentNodes == null || environmentNodes.isEmpty()) {
                throw new IllegalArgumentException(
                        String.format("Pod '%s' not found in namespace '%s'", podName, namespace));
            }

            DiscoveryNode node = environmentNodes.get(0);
            List<DescendantTarget> descendantTargets = node.descendantTargets();

            if (descendantTargets == null || descendantTargets.isEmpty()) {
                throw new IllegalArgumentException(
                        String.format(
                                "No targets found for pod '%s' in namespace '%s'",
                                podName, namespace));
            }

            // Prefer Agent instances over JMX instances by sorting
            Target target =
                    descendantTargets.stream()
                            .map(DescendantTarget::target)
                            .filter(t -> t != null)
                            .sorted(
                                    (t1, t2) ->
                                            Boolean.compare(
                                                    Boolean.TRUE.equals(t2.agent()),
                                                    Boolean.TRUE.equals(t1.agent())))
                            .findFirst()
                            .orElse(null);

            if (target == null) {
                throw new IllegalArgumentException(
                        String.format(
                                "Target data is null for pod '%s' in namespace '%s'",
                                podName, namespace));
            }

            Long targetId = target.id();
            String jvmId = target.jvmId();

            if (targetId == null) {
                throw new IllegalArgumentException(
                        String.format(
                                "Target ID is null for pod '%s' in namespace '%s'",
                                podName, namespace));
            }

            if (jvmId == null || jvmId.isEmpty()) {
                throw new IllegalArgumentException(
                        String.format(
                                "JVM ID is null or empty for pod '%s' in namespace '%s'",
                                podName, namespace));
            }

            return new TargetInfo(targetId, jvmId);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "Failed to query target information for pod '%s' in namespace '%s'",
                            podName, namespace),
                    e);
        }
    }

    private record TargetInfo(long targetId, String jvmId) {}
}
