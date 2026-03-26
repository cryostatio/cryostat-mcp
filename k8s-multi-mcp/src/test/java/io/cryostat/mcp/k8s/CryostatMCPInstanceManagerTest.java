package io.cryostat.mcp.k8s;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cryostat.mcp.CryostatMCP;
import io.cryostat.mcp.CryostatRESTClient;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryostatMCPInstanceManagerTest {

    @Mock private CryostatInstanceDiscovery discovery;

    @Mock private ClientCredentialsContext credentialsContext;

    @Mock private ObjectMapper mapper;

    @Mock private RestClientBuilder restClientBuilder;

    @Mock private CryostatRESTClient restClient;

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
        manager.credentialsContext = credentialsContext;
        manager.mapper = mapper;
    }

    @Test
    void testCreateInstanceSuccess() {
        try (MockedStatic<RestClientBuilder> mockedBuilder =
                mockStatic(RestClientBuilder.class)) {
            mockedBuilder.when(RestClientBuilder::newBuilder).thenReturn(restClientBuilder);
            when(restClientBuilder.baseUri(any(URI.class))).thenReturn(restClientBuilder);
            when(restClientBuilder.build(CryostatRESTClient.class)).thenReturn(restClient);
            when(discovery.findByNamespace("app-namespace")).thenReturn(Optional.of(testInstance));
            when(credentialsContext.hasCredentials()).thenReturn(false);

            CryostatMCP mcp = manager.createInstance("app-namespace");

            assertNotNull(mcp);
            verify(discovery).findByNamespace("app-namespace");
        }
    }

    @Test
    void testCreateInstanceWithCredentials() {
        try (MockedStatic<RestClientBuilder> mockedBuilder =
                mockStatic(RestClientBuilder.class)) {
            mockedBuilder.when(RestClientBuilder::newBuilder).thenReturn(restClientBuilder);
            when(restClientBuilder.baseUri(any(URI.class))).thenReturn(restClientBuilder);
            when(restClientBuilder.register(any(Object.class))).thenReturn(restClientBuilder);
            when(restClientBuilder.build(CryostatRESTClient.class)).thenReturn(restClient);
            when(discovery.findByNamespace("app-namespace")).thenReturn(Optional.of(testInstance));
            when(credentialsContext.hasCredentials()).thenReturn(true);
            when(credentialsContext.getAuthorizationHeader()).thenReturn("Bearer test-token");

            CryostatMCP mcp = manager.createInstance("app-namespace");

            assertNotNull(mcp);
            verify(credentialsContext, atLeastOnce()).hasCredentials();
            verify(credentialsContext, atLeastOnce()).getAuthorizationHeader();
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
        try (MockedStatic<RestClientBuilder> mockedBuilder =
                mockStatic(RestClientBuilder.class)) {
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

            mockedBuilder.when(RestClientBuilder::newBuilder).thenReturn(restClientBuilder);
            when(restClientBuilder.baseUri(any(URI.class))).thenReturn(restClientBuilder);
            when(restClientBuilder.build(CryostatRESTClient.class)).thenReturn(restClient);
            when(discovery.findByNamespace("app1")).thenReturn(Optional.of(instance1));
            when(discovery.findByNamespace("app2")).thenReturn(Optional.of(instance2));
            when(credentialsContext.hasCredentials()).thenReturn(false);

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
        try (MockedStatic<RestClientBuilder> mockedBuilder =
                mockStatic(RestClientBuilder.class)) {
            mockedBuilder.when(RestClientBuilder::newBuilder).thenReturn(restClientBuilder);
            when(restClientBuilder.baseUri(any(URI.class))).thenReturn(restClientBuilder);
            when(restClientBuilder.build(CryostatRESTClient.class)).thenReturn(restClient);
            when(discovery.findByNamespace("app-namespace")).thenReturn(Optional.of(testInstance));
            when(credentialsContext.hasCredentials()).thenReturn(false);

            CryostatMCP mcp1 = manager.createInstance("app-namespace");
            CryostatMCP mcp2 = manager.createInstance("app-namespace");

            assertNotNull(mcp1);
            assertNotNull(mcp2);
            assertNotSame(mcp1, mcp2);
            verify(discovery, times(2)).findByNamespace("app-namespace");
        }
    }
}
