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
import java.util.Optional;

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
 * Uses Quarkus Cache (Caffeine) to cache resolutions for 60 seconds. Cache key is (namespace,
 * podName) only - useAuditLog is not part of the cache key since a Pod's identifiers don't change
 * based on how we query for them.
 */
@ApplicationScoped
public class PodNameResolver {

    private static final Logger LOG = Logger.getLogger(PodNameResolver.class);

    @Inject CryostatMCPInstanceManager instanceManager;

    /**
     * Resolve a Pod name to its JVM hash ID. Results are cached for 60 seconds using Quarkus Cache
     * (Caffeine). Cache is shared regardless of useAuditLog value - once resolved, the result is
     * cached.
     *
     * @param namespace The namespace containing the Pod
     * @param podName The name of the Pod
     * @return The JVM hash ID
     * @throws IllegalArgumentException if no target found for the Pod
     */
    @CacheResult(cacheName = "pod-name-resolution")
    public String resolvePodNameToJvmId(@CacheKey String namespace, @CacheKey String podName) {
        return resolvePodNameToJvmIdInternal(namespace, podName, false);
    }

    /**
     * Resolve a Pod name to its JVM hash ID with explicit audit log control. Results are cached for
     * 60 seconds using Quarkus Cache (Caffeine). Cache is shared regardless of useAuditLog value -
     * once resolved, the result is cached.
     *
     * @param namespace The namespace containing the Pod
     * @param podName The name of the Pod
     * @param useAuditLog Whether to query historical targets from audit log
     * @return The JVM hash ID
     * @throws IllegalArgumentException if no target found for the Pod
     */
    @CacheResult(cacheName = "pod-name-resolution")
    public String resolvePodNameToJvmId(
            @CacheKey String namespace, @CacheKey String podName, boolean useAuditLog) {
        return resolvePodNameToJvmIdInternal(namespace, podName, useAuditLog);
    }

    /**
     * Internal method that performs the actual resolution to JVM ID. Not cached directly - caching
     * happens at the public method level.
     */
    private String resolvePodNameToJvmIdInternal(
            String namespace, String podName, boolean useAuditLog) {
        LOG.debugf(
                "Resolving Pod '%s' in namespace '%s' to jvmId (useAuditLog=%s)",
                podName, namespace, useAuditLog);

        Target target =
                findTargetByPodName(namespace, podName, useAuditLog)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                buildErrorMessage(
                                                        podName, namespace, useAuditLog)));

        LOG.debugf("Resolved Pod '%s' to jvmId '%s'", podName, target.jvmId());
        return target.jvmId();
    }

    /**
     * Resolve a Pod name to its Target ID. Results are cached for 60 seconds using Quarkus Cache
     * (Caffeine). Cache is shared regardless of useAuditLog value - once resolved, the result is
     * cached.
     *
     * @param namespace The namespace containing the Pod
     * @param podName The name of the Pod
     * @return The Target ID
     * @throws IllegalArgumentException if no target found for the Pod
     */
    @CacheResult(cacheName = "pod-name-resolution")
    public Long resolvePodNameToTargetId(@CacheKey String namespace, @CacheKey String podName) {
        return resolvePodNameToTargetIdInternal(namespace, podName, false);
    }

    /**
     * Resolve a Pod name to its Target ID with explicit audit log control. Results are cached for
     * 60 seconds using Quarkus Cache (Caffeine). Cache is shared regardless of useAuditLog value -
     * once resolved, the result is cached.
     *
     * @param namespace The namespace containing the Pod
     * @param podName The name of the Pod
     * @param useAuditLog Whether to query historical targets from audit log
     * @return The Target ID
     * @throws IllegalArgumentException if no target found for the Pod
     */
    @CacheResult(cacheName = "pod-name-resolution")
    public Long resolvePodNameToTargetId(
            @CacheKey String namespace, @CacheKey String podName, boolean useAuditLog) {
        return resolvePodNameToTargetIdInternal(namespace, podName, useAuditLog);
    }

    /**
     * Internal method that performs the actual resolution to Target ID. Not cached directly -
     * caching happens at the public method level.
     */
    private Long resolvePodNameToTargetIdInternal(
            String namespace, String podName, boolean useAuditLog) {
        LOG.debugf(
                "Resolving Pod '%s' in namespace '%s' to targetId (useAuditLog=%s)",
                podName, namespace, useAuditLog);

        Target target =
                findTargetByPodName(namespace, podName, useAuditLog)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                buildErrorMessage(
                                                        podName, namespace, useAuditLog)));

        LOG.debugf("Resolved Pod '%s' to targetId %d", podName, target.id());
        return target.id();
    }

    /**
     * Invalidate cached resolution for a specific Pod. Useful when a Pod is known to have changed.
     */
    @CacheInvalidate(cacheName = "pod-name-resolution")
    public void invalidateCache(@CacheKey String namespace, @CacheKey String podName) {
        LOG.debugf("Invalidating cache for Pod '%s' in namespace '%s'", podName, namespace);
    }

    /** Invalidate all cached resolutions. */
    @CacheInvalidateAll(cacheName = "pod-name-resolution")
    public void invalidateAllCache() {
        LOG.debug("Invalidating all Pod name resolution cache entries");
    }

    /**
     * Find a target by Pod name using the Cryostat GraphQL API.
     *
     * @param namespace The namespace containing the Pod
     * @param podName The name of the Pod
     * @param useAuditLog Whether to query historical targets from audit log
     * @return Optional containing the Target if found
     */
    private Optional<Target> findTargetByPodName(
            String namespace, String podName, boolean useAuditLog) {
        try {
            CryostatMCP mcp = instanceManager.createInstance(namespace);

            // Query Cryostat for targets matching the Pod name
            // useAuditLog=true enables querying historical targets from audit log
            List<DiscoveryNode> nodes =
                    mcp.listTargets(null, null, List.of(podName), null, useAuditLog);

            // Find the first node with a target
            Optional<Target> target =
                    nodes.stream()
                            .filter(node -> node.target() != null)
                            .map(DiscoveryNode::target)
                            .findFirst();

            if (nodes.size() > 1 && target.isPresent()) {
                LOG.warnf(
                        "Multiple discovery nodes found for Pod '%s' in namespace '%s'"
                                + " (useAuditLog=%s). Using first match with target: %s",
                        podName, namespace, useAuditLog, target.get().connectUrl());
            }

            return target;
        } catch (Exception e) {
            LOG.errorf(
                    e,
                    "Failed to find target for Pod '%s' in namespace '%s' (useAuditLog=%s)",
                    podName,
                    namespace,
                    useAuditLog);
            return Optional.empty();
        }
    }

    /**
     * Build a helpful error message when a Pod cannot be resolved.
     *
     * @param podName The Pod name that couldn't be resolved
     * @param namespace The namespace that was searched
     * @param useAuditLog Whether audit log was queried
     * @return A detailed error message with troubleshooting suggestions
     */
    private String buildErrorMessage(String podName, String namespace, boolean useAuditLog) {
        StringBuilder msg = new StringBuilder();
        msg.append("No target found for Pod '")
                .append(podName)
                .append("' in namespace '")
                .append(namespace)
                .append("'. ");
        msg.append("Possible reasons:\n");
        msg.append("1. Pod does not exist in the namespace\n");
        msg.append("2. Pod is not a JVM application\n");
        msg.append("3. Cryostat has not discovered the Pod yet\n");
        msg.append("4. Pod name is misspelled");

        if (!useAuditLog) {
            msg.append("\n5. Pod may have been terminated (try with useAuditLog=true)");
        }

        return msg.toString();
    }
}
