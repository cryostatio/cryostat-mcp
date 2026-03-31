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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.cryostat.mcp.CryostatGraphQLClient;
import io.cryostat.mcp.CryostatMCP;
import io.cryostat.mcp.CryostatRESTClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.graphql.client.typesafe.api.TypesafeGraphQLClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryostatMCPInstanceManagerTest {

    @Mock private CryostatInstanceDiscovery discovery;

    @Mock private ObjectMapper mapper;

    @Mock private RestClientBuilder restClientBuilder;

    @Mock private TypesafeGraphQLClientBuilder graphqlClientBuilder;

    @Mock private CryostatRESTClient restClient;

    @Mock private CryostatGraphQLClient graphqlClient;

    private CryostatMCPInstanceManager manager;

    private CryostatInstance testInstance;

    @BeforeEach
    void setUp() {
        testInstance =
                new CryostatInstance(
                        "test-cryostat",
                        "test-namespace",
                        "http://test-cryostat.test-namespace.svc:8181",
                        Set.of("test-namespace", "app-namespace"));
        manager = new CryostatMCPInstanceManager();
        manager.discovery = discovery;
        manager.mapper = mapper;
        manager.authorizationHeaderConfig = Optional.empty();
        manager.graphqlPath = "/api/v4/graphql";
    }

    @Test
    void testCreateInstanceSuccess() {
        try (MockedStatic<RestClientBuilder> mockedRestBuilder =
                        mockStatic(RestClientBuilder.class);
                MockedStatic<TypesafeGraphQLClientBuilder> mockedGraphQLBuilder =
                        mockStatic(TypesafeGraphQLClientBuilder.class)) {
            mockedRestBuilder.when(RestClientBuilder::newBuilder).thenReturn(restClientBuilder);
            when(restClientBuilder.baseUri(any(URI.class))).thenReturn(restClientBuilder);
            when(restClientBuilder.followRedirects(anyBoolean())).thenReturn(restClientBuilder);
            when(restClientBuilder.build(CryostatRESTClient.class)).thenReturn(restClient);

            mockedGraphQLBuilder
                    .when(TypesafeGraphQLClientBuilder::newBuilder)
                    .thenReturn(graphqlClientBuilder);
            when(graphqlClientBuilder.endpoint(anyString())).thenReturn(graphqlClientBuilder);
            when(graphqlClientBuilder.build(CryostatGraphQLClient.class)).thenReturn(graphqlClient);

            when(discovery.findByNamespace("app-namespace")).thenReturn(Optional.of(testInstance));

            CryostatMCP mcp = manager.createInstance("app-namespace");

            assertNotNull(mcp);
            verify(discovery).findByNamespace("app-namespace");
        }
    }

    @Test
    void testCreateInstanceWithCredentials() {
        try (MockedStatic<RestClientBuilder> mockedRestBuilder =
                        mockStatic(RestClientBuilder.class);
                MockedStatic<TypesafeGraphQLClientBuilder> mockedGraphQLBuilder =
                        mockStatic(TypesafeGraphQLClientBuilder.class)) {
            mockedRestBuilder.when(RestClientBuilder::newBuilder).thenReturn(restClientBuilder);
            when(restClientBuilder.baseUri(any(URI.class))).thenReturn(restClientBuilder);
            when(restClientBuilder.followRedirects(anyBoolean())).thenReturn(restClientBuilder);
            when(restClientBuilder.register(any(Object.class))).thenReturn(restClientBuilder);
            when(restClientBuilder.build(CryostatRESTClient.class)).thenReturn(restClient);

            mockedGraphQLBuilder
                    .when(TypesafeGraphQLClientBuilder::newBuilder)
                    .thenReturn(graphqlClientBuilder);
            when(graphqlClientBuilder.endpoint(anyString())).thenReturn(graphqlClientBuilder);
            when(graphqlClientBuilder.header(anyString(), anyString()))
                    .thenReturn(graphqlClientBuilder);
            when(graphqlClientBuilder.build(CryostatGraphQLClient.class)).thenReturn(graphqlClient);

            when(discovery.findByNamespace("app-namespace")).thenReturn(Optional.of(testInstance));
            manager.authorizationHeaderConfig = Optional.of("Bearer test-token");

            CryostatMCP mcp = manager.createInstance("app-namespace");

            assertNotNull(mcp);
            verify(restClientBuilder).register(any(ClientHeadersFactory.class));
            verify(graphqlClientBuilder).header("Authorization", "Bearer test-token");
        }
    }

    @Test
    void testCreateInstanceNoInstanceFound() {
        when(discovery.findByNamespace("unknown-namespace")).thenReturn(Optional.empty());
        when(discovery.getAllInstances()).thenReturn(List.of(testInstance));

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> manager.createInstance("unknown-namespace"));

        assertTrue(exception.getMessage().contains("No Cryostat instance found"));
        assertTrue(exception.getMessage().contains("unknown-namespace"));
        verify(discovery).findByNamespace("unknown-namespace");
        verify(discovery).getAllInstances();
    }

    @Test
    void testCreateInstanceForDifferentNamespaces() {
        try (MockedStatic<RestClientBuilder> mockedRestBuilder =
                        mockStatic(RestClientBuilder.class);
                MockedStatic<TypesafeGraphQLClientBuilder> mockedGraphQLBuilder =
                        mockStatic(TypesafeGraphQLClientBuilder.class)) {
            CryostatInstance instance1 =
                    new CryostatInstance(
                            "cryostat-1",
                            "ns1",
                            "http://cryostat-1.ns1.svc:8181",
                            Set.of("ns1", "app1"));
            CryostatInstance instance2 =
                    new CryostatInstance(
                            "cryostat-2",
                            "ns2",
                            "http://cryostat-2.ns2.svc:8181",
                            Set.of("ns2", "app2"));

            mockedRestBuilder.when(RestClientBuilder::newBuilder).thenReturn(restClientBuilder);
            when(restClientBuilder.baseUri(any(URI.class))).thenReturn(restClientBuilder);
            when(restClientBuilder.followRedirects(anyBoolean())).thenReturn(restClientBuilder);
            when(restClientBuilder.build(CryostatRESTClient.class)).thenReturn(restClient);

            mockedGraphQLBuilder
                    .when(TypesafeGraphQLClientBuilder::newBuilder)
                    .thenReturn(graphqlClientBuilder);
            when(graphqlClientBuilder.endpoint(anyString())).thenReturn(graphqlClientBuilder);
            when(graphqlClientBuilder.build(CryostatGraphQLClient.class)).thenReturn(graphqlClient);

            when(discovery.findByNamespace("app1")).thenReturn(Optional.of(instance1));
            when(discovery.findByNamespace("app2")).thenReturn(Optional.of(instance2));

            CryostatMCP mcp1 = manager.createInstance("app1");
            CryostatMCP mcp2 = manager.createInstance("app2");

            assertNotNull(mcp1);
            assertNotNull(mcp2);
            assertNotSame(mcp1, mcp2);
            verify(discovery).findByNamespace("app1");
            verify(discovery).findByNamespace("app2");
        }
    }

    @Test
    void testCreateInstanceMultipleTimes() {
        try (MockedStatic<RestClientBuilder> mockedRestBuilder =
                        mockStatic(RestClientBuilder.class);
                MockedStatic<TypesafeGraphQLClientBuilder> mockedGraphQLBuilder =
                        mockStatic(TypesafeGraphQLClientBuilder.class)) {
            mockedRestBuilder.when(RestClientBuilder::newBuilder).thenReturn(restClientBuilder);
            when(restClientBuilder.baseUri(any(URI.class))).thenReturn(restClientBuilder);
            when(restClientBuilder.followRedirects(anyBoolean())).thenReturn(restClientBuilder);
            when(restClientBuilder.build(CryostatRESTClient.class)).thenReturn(restClient);

            mockedGraphQLBuilder
                    .when(TypesafeGraphQLClientBuilder::newBuilder)
                    .thenReturn(graphqlClientBuilder);
            when(graphqlClientBuilder.endpoint(anyString())).thenReturn(graphqlClientBuilder);
            when(graphqlClientBuilder.build(CryostatGraphQLClient.class)).thenReturn(graphqlClient);

            when(discovery.findByNamespace("app-namespace")).thenReturn(Optional.of(testInstance));

            CryostatMCP mcp1 = manager.createInstance("app-namespace");
            CryostatMCP mcp2 = manager.createInstance("app-namespace");

            assertNotNull(mcp1);
            assertNotNull(mcp2);
            assertSame(mcp1, mcp2);
            verify(discovery, times(1)).findByNamespace("app-namespace");
        }
    }
}
