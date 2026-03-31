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

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.cryostat.mcp.CryostatMCP;
import io.cryostat.mcp.CryostatRESTClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.graphql.client.typesafe.api.TypesafeGraphQLClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.jboss.logging.Logger;

/**
 * Manages creation of CryostatMCP instances for different Cryostat deployments. Routes requests to
 * the appropriate Cryostat instance based on namespace.
 */
@ApplicationScoped
public class CryostatMCPInstanceManager {

    private static final Logger LOG = Logger.getLogger(CryostatMCPInstanceManager.class);

    private final ConcurrentHashMap<String, CryostatMCP> instanceCache = new ConcurrentHashMap<>();

    @Inject CryostatInstanceDiscovery discovery;
    @Inject ObjectMapper mapper;

    @ConfigProperty(name = "k8s.mux.authorization.header")
    Optional<String> authorizationHeaderConfig;

    @ConfigProperty(name = "cryostat.graphql.path", defaultValue = "/api/v4/graphql")
    String graphqlPath;

    /**
     * Get or create a CryostatMCP instance for the given target application namespace. Instances
     * are cached to avoid recreating clients repeatedly. Finds the appropriate Cryostat instance
     * that monitors the specified namespace and configures clients with credentials.
     *
     * @param namespace the target application namespace (where monitored applications run)
     * @return a configured CryostatMCP instance
     * @throws IllegalStateException if no Cryostat instance is found monitoring the namespace
     */
    public CryostatMCP createInstance(String namespace) {
        return createInstance(namespace, null);
    }

    /**
     * Get or create a CryostatMCP instance for the given target application namespace with explicit
     * authorization header. Instances are cached to avoid recreating clients repeatedly. Finds the
     * appropriate Cryostat instance that monitors the specified namespace and configures clients
     * with credentials.
     *
     * @param namespace the target application namespace (where monitored applications run)
     * @param authorizationHeader the Authorization header to use, or null to fall back to
     *     ConfigProperty
     * @return a configured CryostatMCP instance
     * @throws IllegalStateException if no Cryostat instance is found monitoring the namespace
     */
    public CryostatMCP createInstance(String namespace, String authorizationHeader) {
        // Note: We don't cache instances with explicit auth headers to avoid credential leakage
        // between requests
        if (authorizationHeader != null) {
            return createNewInstance(namespace, authorizationHeader);
        }
        return instanceCache.computeIfAbsent(namespace, ns -> createNewInstance(ns, null));
    }

    private CryostatMCP createNewInstance(String namespace, String authorizationHeader) {
        LOG.infof(
                "Creating CryostatMCP instance for namespace '%s' on thread %s with %s auth header",
                namespace,
                Thread.currentThread().getName(),
                authorizationHeader != null ? "explicit" : "context-based");

        Optional<CryostatInstance> instanceOpt = discovery.findByNamespace(namespace);

        if (instanceOpt.isEmpty()) {
            String message =
                    String.format(
                            "No Cryostat instance found for namespace '%s'. Available instances:"
                                    + " %s",
                            namespace, discovery.getAllInstances());
            LOG.error(message);
            throw new IllegalStateException(message);
        }

        CryostatInstance instance = instanceOpt.get();
        LOG.infof(
                "Found Cryostat instance '%s' at %s for namespace '%s'",
                instance.name(), instance.applicationUrl(), namespace);

        CryostatRESTClient restClient = createRESTClient(instance, authorizationHeader);
        CryostatGraphQLClientImpl graphqlClient =
                createGraphQLClient(instance, authorizationHeader);

        return new CryostatMCP(restClient, graphqlClient, mapper);
    }

    private CryostatRESTClient createRESTClient(
            CryostatInstance instance, String authorizationHeader) {
        RestClientBuilder builder =
                RestClientBuilder.newBuilder()
                        .baseUri(URI.create(instance.applicationUrl()))
                        .followRedirects(true);

        // Use explicit auth header if provided, otherwise fall back to config property
        String authHeader =
                authorizationHeader != null
                        ? authorizationHeader
                        : authorizationHeaderConfig.orElse(null);

        LOG.infof(
                "Creating REST client for %s with auth header: %s (source: %s)",
                instance.applicationUrl(),
                authHeader != null ? "present" : "null",
                authorizationHeader != null ? "explicit" : "config");

        if (authHeader != null && !authHeader.isEmpty()) {
            // Use builder.header() to set the Authorization header directly
            // This is the correct way for programmatic REST client configuration in Quarkus
            builder.header("Authorization", authHeader);
            LOG.infof("Set Authorization header on REST client builder");
        } else {
            LOG.warnf(
                    "No authorization header available for %s - requests will likely fail with 403",
                    instance.applicationUrl());
        }

        return builder.build(CryostatRESTClient.class);
    }

    private CryostatGraphQLClientImpl createGraphQLClient(
            CryostatInstance instance, String authorizationHeader) {
        String graphqlEndpoint = instance.applicationUrl() + graphqlPath;

        TypesafeGraphQLClientBuilder builder =
                TypesafeGraphQLClientBuilder.newBuilder()
                        .endpoint(graphqlEndpoint)
                        // Use named TLS configuration for certificate trust settings
                        .configKey("notls");

        // Use explicit auth header if provided, otherwise fall back to config property
        String authHeader =
                authorizationHeader != null
                        ? authorizationHeader
                        : authorizationHeaderConfig.orElse(null);

        if (authHeader != null && !authHeader.isEmpty()) {
            LOG.debugf(
                    "Forwarding Authorization header to GraphQL client for %s (source: %s)",
                    graphqlEndpoint, authorizationHeader != null ? "explicit" : "config");
            builder.header("Authorization", authHeader);
        }

        return builder.build(CryostatGraphQLClientImpl.class);
    }
}
