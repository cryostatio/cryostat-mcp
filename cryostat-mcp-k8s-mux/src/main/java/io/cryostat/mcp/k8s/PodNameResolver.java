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

import java.util.List;

import io.cryostat.mcp.CryostatMCP;
import io.cryostat.mcp.model.graphql.DiscoveryNode;
import io.cryostat.mcp.model.graphql.Target;

import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheKey;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Service for resolving Kubernetes Pod names to Cryostat Target identifiers (JVM ID and Target ID).
 * Uses Quarkus Cache (Caffeine) to cache resolutions. A single cache entry holds both identifiers
 * so that callers needing both values pay only one GraphQL call per cold cache miss.
 *
 * <p>The lookup first queries the live dataset; if no result is found it retries against Cryostat's
 * Hibernate Envers audit log. A failed lookup is never cached — the Pod may appear (or reappear) in
 * the dataset later.
 */
@ApplicationScoped
public class PodNameResolver {

    @Inject Logger log;
    @Inject CryostatMCPInstanceManager instanceManager;

    /**
     * Resolve a Pod name to its JVM hash ID.
     *
     * @param namespace The namespace containing the Pod
     * @param podName The name of the Pod
     * @return The JVM hash ID
     * @throws IllegalArgumentException if no target found for the Pod
     */
    public String resolvePodNameToJvmId(String namespace, String podName) {
        return resolveTarget(namespace, podName).jvmId();
    }

    /**
     * Resolve a Pod name to its Target ID.
     *
     * @param namespace The namespace containing the Pod
     * @param podName The name of the Pod
     * @return The Target ID
     * @throws IllegalArgumentException if no target found for the Pod
     */
    public Long resolvePodNameToTargetId(String namespace, String podName) {
        return resolveTarget(namespace, podName).targetId();
    }

    /**
     * Resolve a Pod name to a {@link TargetInfo} holding both the Target ID and JVM hash ID. This
     * is the single cache entry point; callers that need both identifiers pay only one GraphQL call
     * per cold cache miss.
     *
     * <p>The live dataset is tried first. If no result is found the audit log is queried as a
     * fallback. A successful result is cached regardless of which path found it. A failure is not
     * cached.
     *
     * @param namespace The namespace containing the Pod
     * @param podName The name of the Pod
     * @return A {@link TargetInfo} containing the Target ID and JVM hash ID
     * @throws IllegalArgumentException if no target found for the Pod in either dataset
     */
    @CacheResult(cacheName = "pod-name-resolution")
    public TargetInfo resolveTarget(@CacheKey String namespace, @CacheKey String podName) {
        List<DiscoveryNode> nodes = queryTargetNodes(namespace, podName, false);

        if (nodes == null || nodes.isEmpty()) {
            log.debugf(
                    "Pod '%s' not found in live dataset for namespace '%s', retrying with audit"
                            + " log",
                    podName, namespace);
            nodes = queryTargetNodes(namespace, podName, true);
        }

        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException(buildErrorMessage(podName, namespace));
        }

        if (nodes.size() > 1) {
            log.debugf(
                    "Multiple discovery nodes found for Pod '%s' in namespace '%s'. Using Agent"
                            + " instance if present, otherwise first match.",
                    podName, namespace);
        }

        Target target =
                nodes.stream()
                        .map(DiscoveryNode::target)
                        .filter(t -> t != null)
                        .sorted(
                                (t1, t2) ->
                                        Boolean.compare(
                                                Boolean.TRUE.equals(t2.agent()),
                                                Boolean.TRUE.equals(t1.agent())))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                buildErrorMessage(podName, namespace)));

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
    }

    /**
     * Invalidate cached resolution for a specific Pod. Useful when a Pod is known to have changed.
     */
    @CacheInvalidate(cacheName = "pod-name-resolution")
    public void invalidateCache(@CacheKey String namespace, @CacheKey String podName) {}

    /** Invalidate all cached resolutions. */
    @CacheInvalidateAll(cacheName = "pod-name-resolution")
    public void invalidateAllCache() {}

    private List<DiscoveryNode> queryTargetNodes(
            String namespace, String podName, boolean useAuditLog) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        return mcp.listTargets(null, null, null, List.of(podName), null, null, useAuditLog);
    }

    private String buildErrorMessage(String podName, String namespace) {
        return "No target found for Pod '"
                + podName
                + "' in namespace '"
                + namespace
                + "'. "
                + "Possible reasons:\n"
                + "1. Pod does not exist in the namespace\n"
                + "2. Pod is not a JVM application\n"
                + "3. Cryostat has not discovered the Pod yet\n"
                + "4. Pod name is misspelled\n"
                + "5. Pod may have been terminated before Cryostat observed it";
    }

    /** Holds the Target ID and JVM hash ID for a resolved Pod. */
    public record TargetInfo(long targetId, String jvmId) {}
}
