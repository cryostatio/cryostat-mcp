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

    private final ConcurrentHashMap<String, CryostatMCP> instanceCache = new ConcurrentHashMap<>();

    @Inject Logger log;
    @Inject CryostatInstanceDiscovery discovery;
    @Inject ObjectMapper mapper;
    @Inject CryostatAuthorization authorization;

    @ConfigProperty(name = "k8s.mux.authorization.header")
    Optional<String> staticAuthorizationHeader;

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
        return instanceCache.computeIfAbsent(
                targetNamespaceCacheKey(namespace), ignored -> createNewInstance(namespace));
    }

    /**
     * Get or create a CryostatMCP instance for a known Cryostat instance. This is used by
     * non-directed tools that already operate over discovered Cryostat CRs instead of routing from
     * an application namespace.
     *
     * @param instance the discovered Cryostat instance
     * @return a configured CryostatMCP instance
     */
    public CryostatMCP createInstance(CryostatInstance instance) {
        return instanceCache.computeIfAbsent(
                cryostatInstanceCacheKey(instance), ignored -> createNewInstance(instance));
    }

    String getAuthorizationHeader() {
        String passthrough = authorization.getPassthroughAuthorizationHeader();
        if (passthrough != null) {
            return passthrough;
        }
        return staticAuthorizationHeader.filter(header -> !header.isBlank()).orElse(null);
    }

    private CryostatMCP createNewInstance(String namespace) {
        log.debugf(
                "Creating CryostatMCP instance for namespace '%s' on thread %s",
                namespace, Thread.currentThread().getName());

        Optional<CryostatInstance> instanceOpt = discovery.findByNamespace(namespace);

        if (instanceOpt.isEmpty()) {
            String message =
                    String.format(
                            "No Cryostat instance found for namespace '%s'. Available instances:"
                                    + " %s",
                            namespace, discovery.getAllInstances());
            log.error(message);
            throw new IllegalStateException(message);
        }

        CryostatInstance instance = instanceOpt.get();
        return createNewInstance(instance);
    }

    private CryostatMCP createNewInstance(CryostatInstance instance) {
        log.debugf(
                "Creating CryostatMCP instance for Cryostat '%s/%s' at %s on thread %s",
                instance.namespace(),
                instance.name(),
                instance.applicationUrl(),
                Thread.currentThread().getName());

        CryostatRESTClient restClient = createRESTClient(instance);
        CryostatGraphQLClientImpl graphqlClient = createGraphQLClient(instance);

        return CryostatMCP.withAuthorizationHeaderSupplier(
                URI.create(instance.applicationUrl()),
                this::getAuthorizationHeader,
                restClient,
                graphqlClient,
                mapper);
    }

    private String targetNamespaceCacheKey(String namespace) {
        return "target-namespace:" + namespace;
    }

    private String cryostatInstanceCacheKey(CryostatInstance instance) {
        return "cryostat-instance:" + instance.namespace() + "/" + instance.name();
    }

    private CryostatRESTClient createRESTClient(CryostatInstance instance) {
        RestClientBuilder builder =
                RestClientBuilder.newBuilder()
                        .baseUri(URI.create(instance.applicationUrl()))
                        .followRedirects(true);
        builder.register(new CryostatAuthorizationFilter(this::getAuthorizationHeader));

        return builder.build(CryostatRESTClient.class);
    }

    private CryostatGraphQLClientImpl createGraphQLClient(CryostatInstance instance) {
        String graphqlEndpoint = instance.applicationUrl() + graphqlPath;
        AuthorizationAwareGraphQLClient.Delegate delegate =
                newGraphQLClientBuilder(graphqlEndpoint)
                        .build(AuthorizationAwareGraphQLClient.Delegate.class);
        return new AuthorizationAwareGraphQLClient(delegate, this::getAuthorizationHeader);
    }

    private TypesafeGraphQLClientBuilder newGraphQLClientBuilder(String graphqlEndpoint) {
        return TypesafeGraphQLClientBuilder.newBuilder()
                .endpoint(graphqlEndpoint)
                // Use named TLS configuration for certificate trust settings
                .configKey("notls");
    }
}
