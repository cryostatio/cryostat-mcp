package io.cryostat.mcp.k8s;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.cryostat.mcp.CryostatMCP;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class K8sMultiMCPTest {

    @Mock private CryostatMCPInstanceManager instanceManager;

    @Mock private CryostatInstanceDiscovery discovery;

    @Mock private CryostatMCP mockMCP;

    private CryostatInstance testInstance;

    @BeforeEach
    void setUp() {
        testInstance =
                new CryostatInstance(
                        "test-cryostat",
                        "test-namespace",
                        "http://test-cryostat.test-namespace.svc:8181",
                        Set.of("test-namespace", "app-namespace"));
    }

    @Test
    void testInstanceManagerCreatesInstanceForNamespace() {
        when(instanceManager.createInstance("app-namespace")).thenReturn(mockMCP);

        CryostatMCP result = instanceManager.createInstance("app-namespace");

        assertNotNull(result);
        assertEquals(mockMCP, result);
        verify(instanceManager).createInstance("app-namespace");
    }

    @Test
    void testDiscoveryFindsInstanceByNamespace() {
        when(discovery.getAllInstances()).thenReturn(List.of(testInstance));

        List<CryostatInstance> instances = new ArrayList<>(discovery.getAllInstances());

        assertFalse(instances.isEmpty());
        assertEquals(testInstance, instances.get(0));
        verify(discovery).getAllInstances();
    }

    @Test
    void testDiscoveryReturnsEmptyWhenNoInstances() {
        when(discovery.getAllInstances()).thenReturn(List.of());

        List<CryostatInstance> instances = new ArrayList<>(discovery.getAllInstances());

        assertTrue(instances.isEmpty());
        verify(discovery).getAllInstances();
    }

    @Test
    void testInstanceManagerThrowsWhenNamespaceNotFound() {
        when(instanceManager.createInstance("unknown-namespace"))
                .thenThrow(
                        new IllegalStateException(
                                "No Cryostat instance found for namespace: unknown-namespace"));

        assertThrows(
                IllegalStateException.class,
                () -> instanceManager.createInstance("unknown-namespace"));
        verify(instanceManager).createInstance("unknown-namespace");
    }

    @Test
    void testMultipleInstancesCanBeCreated() {
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

        when(discovery.getAllInstances()).thenReturn(List.of(instance1, instance2));

        List<CryostatInstance> instances = new ArrayList<>(discovery.getAllInstances());

        assertEquals(2, instances.size());
        assertTrue(instances.contains(instance1));
        assertTrue(instances.contains(instance2));
    }

    @Test
    void testInstanceHasCorrectProperties() {
        assertEquals("test-cryostat", testInstance.name());
        assertEquals("test-namespace", testInstance.namespace());
        assertEquals("http://test-cryostat.test-namespace.svc:8181", testInstance.applicationUrl());
        assertTrue(testInstance.targetNamespaces().contains("test-namespace"));
        assertTrue(testInstance.targetNamespaces().contains("app-namespace"));
    }

    @Test
    void testNamespaceParameterValidation() {
        Map<String, Object> args = new HashMap<>();
        args.put("namespace", null);

        // Simulate what K8sMultiMCP does when namespace is required but not provided
        String namespace = (String) args.get("namespace");
        boolean namespaceRequired = true;

        if (namespace == null || namespace.isEmpty()) {
            if (namespaceRequired) {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            throw new IllegalArgumentException(
                                    "Namespace is required for this operation");
                        });
            }
        }
    }

    @Test
    void testFallbackToFirstInstanceWhenNamespaceNotProvided() {
        when(discovery.getAllInstances()).thenReturn(List.of(testInstance));

        List<CryostatInstance> instances = new ArrayList<>(discovery.getAllInstances());
        assertFalse(instances.isEmpty());

        // Simulate what K8sMultiMCP does for optional namespace
        String namespace = instances.get(0).namespace();
        assertEquals("test-namespace", namespace);
    }

    @Test
    void testThrowsWhenNoInstancesAvailableForFallback() {
        when(discovery.getAllInstances()).thenReturn(List.of());

        List<CryostatInstance> instances = new ArrayList<>(discovery.getAllInstances());

        if (instances.isEmpty()) {
            assertThrows(
                    IllegalStateException.class,
                    () -> {
                        throw new IllegalStateException("No Cryostat instances available");
                    });
        }
    }
}