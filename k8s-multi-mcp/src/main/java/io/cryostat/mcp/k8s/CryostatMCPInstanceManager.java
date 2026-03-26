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

import io.cryostat.mcp.CryostatGraphQLClient;
import io.cryostat.mcp.CryostatMCP;
import io.cryostat.mcp.CryostatRESTClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.graphql.client.typesafe.api.TypesafeGraphQLClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;
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
    @Inject ClientCredentialsContext credentialsContext;
    @Inject ObjectMapper mapper;

    /**
     * Get or create a CryostatMCP instance for the given namespace. Instances are cached to avoid
     * recreating clients repeatedly. Finds the appropriate Cryostat instance and configures clients
     * with credentials.
     *
     * @param namespace the target namespace
     * @return a configured CryostatMCP instance
     * @throws IllegalStateException if no Cryostat instance is found for the namespace
     */
    public CryostatMCP createInstance(String namespace) {
        return instanceCache.computeIfAbsent(namespace, this::createNewInstance);
    }

    private CryostatMCP createNewInstance(String namespace) {
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
                "Creating CryostatMCP instance for namespace '%s' using Cryostat '%s' at %s",
                namespace, instance.name(), instance.applicationUrl());

        CryostatRESTClient restClient = createRESTClient(instance);
        CryostatGraphQLClient graphqlClient = createGraphQLClient(instance);

        return new CryostatMCP(restClient, graphqlClient, mapper);
    }

    private CryostatRESTClient createRESTClient(CryostatInstance instance) {
        RestClientBuilder builder =
                RestClientBuilder.newBuilder()
                        .baseUri(URI.create(instance.applicationUrl()))
                        .followRedirects(true);

        if (credentialsContext.hasCredentials()) {
            String authHeader = credentialsContext.getAuthorizationHeader();
            builder.register(
                    new ClientHeadersFactory() {
                        @Override
                        public MultivaluedMap<String, String> update(
                                MultivaluedMap<String, String> incomingHeaders,
                                MultivaluedMap<String, String> clientOutgoingHeaders) {
                            clientOutgoingHeaders.putSingle("Authorization", authHeader);
                            return clientOutgoingHeaders;
                        }
                    });
        }

        return builder.build(CryostatRESTClient.class);
    }

    private CryostatGraphQLClient createGraphQLClient(CryostatInstance instance) {
        String graphqlEndpoint = instance.applicationUrl() + "/api/v2.2/graphql";

        TypesafeGraphQLClientBuilder builder =
                TypesafeGraphQLClientBuilder.newBuilder().endpoint(graphqlEndpoint);

        if (credentialsContext.hasCredentials()) {
            String authHeader = credentialsContext.getAuthorizationHeader();
            builder.header("Authorization", authHeader);
        }

        return builder.build(CryostatGraphQLClient.class);
    }
}
