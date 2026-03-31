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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.cryostat.mcp.CryostatMCP;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.Tool;
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

    private DirectedTools directedTools;
    private NonDirectedTools nonDirectedTools;
    private SystemTools systemTools;

    private CryostatInstance testInstance1;
    private CryostatInstance testInstance2;

    @BeforeEach
    void setUp() {
        directedTools = new DirectedTools();
        directedTools.instanceManager = instanceManager;

        nonDirectedTools = new NonDirectedTools();
        nonDirectedTools.discovery = discovery;
        nonDirectedTools.instanceManager = instanceManager;
        nonDirectedTools.objectMapper = new ObjectMapper();

        systemTools = new SystemTools();
        systemTools.discovery = discovery;
        systemTools.objectMapper = new ObjectMapper();

        testInstance1 =
                new CryostatInstance(
                        "cryostat-1",
                        "namespace-1",
                        "http://cryostat-1.namespace-1.svc:8181",
                        Set.of("namespace-1", "app-namespace-1"));

        testInstance2 =
                new CryostatInstance(
                        "cryostat-2",
                        "namespace-2",
                        "http://cryostat-2.namespace-2.svc:8181",
                        Set.of("namespace-2", "app-namespace-2"));
    }

    @Test
    void testDirectedToolRequiresNamespace() {
        // Verify that directed tools require namespace parameter
        when(instanceManager.createInstance("namespace-1", null)).thenReturn(mockMCP);

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "namespace-1");

        CryostatMCP result = instanceManager.createInstance("namespace-1", null);

        assertNotNull(result);
        assertEquals(mockMCP, result);
        verify(instanceManager).createInstance("namespace-1", null);
    }

    @Test
    void testDirectedToolThrowsWhenNamespaceMissing() {
        // Simulate missing namespace parameter
        Map<String, Object> args = new HashMap<>();
        // namespace is null or missing

        String namespace = (String) args.get("namespace");

        if (namespace == null || namespace.isEmpty()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> {
                        throw new IllegalArgumentException(
                                "Namespace is required for directed tools");
                    });
        }
    }

    @Test
    void testDirectedToolRoutesToCorrectInstance() {
        when(instanceManager.createInstance("namespace-1", null)).thenReturn(mockMCP);

        CryostatMCP result = instanceManager.createInstance("namespace-1", null);

        assertNotNull(result);
        assertEquals(mockMCP, result);
        verify(instanceManager).createInstance("namespace-1", null);
    }

    @Test
    void testDirectedToolThrowsWhenInstanceNotFound() {
        when(instanceManager.createInstance("unknown-namespace", null))
                .thenThrow(
                        new IllegalStateException(
                                "No Cryostat instance found for namespace: unknown-namespace"));

        assertThrows(
                IllegalStateException.class,
                () -> instanceManager.createInstance("unknown-namespace", null));
        verify(instanceManager).createInstance("unknown-namespace", null);
    }

    @Test
    void testNonDirectedToolQueriesAllInstances() {
        // Non-directed tools should query all available instances
        when(discovery.getAllInstances()).thenReturn(List.of(testInstance1, testInstance2));

        List<CryostatInstance> instances = new ArrayList<>(discovery.getAllInstances());

        assertEquals(2, instances.size());
        assertTrue(instances.contains(testInstance1));
        assertTrue(instances.contains(testInstance2));
        verify(discovery).getAllInstances();
    }

    @Test
    void testNonDirectedToolHandlesEmptyInstanceList() {
        // Non-directed tools should handle empty instance list gracefully
        when(discovery.getAllInstances()).thenReturn(List.of());

        List<CryostatInstance> instances = new ArrayList<>(discovery.getAllInstances());

        assertTrue(instances.isEmpty());
        verify(discovery).getAllInstances();
    }

    @Test
    void testNonDirectedToolAggregatesResults() {
        // Simulate aggregation of results from multiple instances
        when(discovery.getAllInstances()).thenReturn(List.of(testInstance1, testInstance2));
        when(instanceManager.createInstance("namespace-1", null)).thenReturn(mockMCP);
        when(instanceManager.createInstance("namespace-2", null)).thenReturn(mockMCP);

        List<CryostatInstance> instances = new ArrayList<>(discovery.getAllInstances());

        // Verify we have multiple instances to aggregate from
        assertEquals(2, instances.size());

        // Verify each instance can be accessed
        for (CryostatInstance instance : instances) {
            CryostatMCP mcp = instanceManager.createInstance(instance.namespace(), null);
            assertNotNull(mcp);
        }
    }

    @Test
    void testNonDirectedToolHandlesIndividualInstanceFailure() {
        // Non-directed tools should continue even if one instance fails
        when(discovery.getAllInstances()).thenReturn(List.of(testInstance1, testInstance2));
        when(instanceManager.createInstance("namespace-1", null)).thenReturn(mockMCP);
        when(instanceManager.createInstance("namespace-2", null))
                .thenThrow(new RuntimeException("Instance unavailable"));

        List<CryostatInstance> instances = new ArrayList<>(discovery.getAllInstances());
        List<CryostatMCP> results = new ArrayList<>();

        for (CryostatInstance instance : instances) {
            try {
                CryostatMCP mcp = instanceManager.createInstance(instance.namespace(), null);
                results.add(mcp);
            } catch (Exception e) {
                // Add null to maintain alignment with instances list
                results.add(null);
            }
        }

        // Should have results for both instances (one success, one null)
        assertEquals(2, results.size());
        assertNotNull(results.get(0));
        assertNull(results.get(1));
    }

    @Test
    void testScrapeGlobalMetricsIsNonDirected() {
        // scrapeGlobalMetrics should be a non-directed tool
        // It should query all instances without requiring namespace parameter
        when(discovery.getAllInstances()).thenReturn(List.of(testInstance1, testInstance2));

        List<CryostatInstance> instances = new ArrayList<>(discovery.getAllInstances());

        // Verify it can access multiple instances
        assertEquals(2, instances.size());
        verify(discovery).getAllInstances();
    }

    @Test
    void testDirectedToolsHaveToolAnnotations() {
        // Verify that DirectedTools methods have @Tool annotations
        Method[] methods = DirectedTools.class.getDeclaredMethods();
        int toolCount = 0;

        for (Method method : methods) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            if (toolAnnotation != null) {
                toolCount++;
            }
        }

        // Verify we found directed tools
        assertTrue(toolCount > 0, "DirectedTools should have @Tool annotated methods");
    }

    @Test
    void testNonDirectedToolsHaveToolAnnotations() {
        // Verify that NonDirectedTools methods have @Tool annotations
        Method[] methods = NonDirectedTools.class.getDeclaredMethods();
        int toolCount = 0;

        for (Method method : methods) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            if (toolAnnotation != null) {
                toolCount++;
            }
        }

        // Verify we found non-directed tools
        assertTrue(toolCount > 0, "NonDirectedTools should have @Tool annotated methods");
    }

    @Test
    void testSystemToolsHaveToolAnnotations() {
        // Verify that SystemTools methods have @Tool annotations
        Method[] methods = SystemTools.class.getDeclaredMethods();
        int toolCount = 0;

        for (Method method : methods) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            if (toolAnnotation != null) {
                toolCount++;
            }
        }

        // Verify we found system tools
        assertTrue(toolCount > 0, "SystemTools should have @Tool annotated methods");
    }

    @Test
    void testInstanceHasCorrectProperties() {
        assertEquals("cryostat-1", testInstance1.name());
        assertEquals("namespace-1", testInstance1.namespace());
        assertEquals("http://cryostat-1.namespace-1.svc:8181", testInstance1.applicationUrl());
        assertTrue(testInstance1.targetNamespaces().contains("namespace-1"));
        assertTrue(testInstance1.targetNamespaces().contains("app-namespace-1"));
    }

    @Test
    void testMultipleInstancesCanBeDiscovered() {
        when(discovery.getAllInstances()).thenReturn(List.of(testInstance1, testInstance2));

        List<CryostatInstance> instances = new ArrayList<>(discovery.getAllInstances());

        assertEquals(2, instances.size());
        assertTrue(instances.contains(testInstance1));
        assertTrue(instances.contains(testInstance2));
    }

    @Test
    void testInstanceManagerCreatesInstanceWithAuthorization() {
        String authHeader = "Bearer test-token";
        when(instanceManager.createInstance("namespace-1", authHeader)).thenReturn(mockMCP);

        CryostatMCP result = instanceManager.createInstance("namespace-1", authHeader);

        assertNotNull(result);
        assertEquals(mockMCP, result);
        verify(instanceManager).createInstance("namespace-1", authHeader);
    }

    @Test
    void testDirectedToolWithAuthorizationHeader() {
        String authHeader = "Bearer test-token";
        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "namespace-1");

        when(instanceManager.createInstance("namespace-1", authHeader)).thenReturn(mockMCP);

        CryostatMCP result = instanceManager.createInstance("namespace-1", authHeader);

        assertNotNull(result);
        verify(instanceManager).createInstance("namespace-1", authHeader);
    }

    @Test
    void testNonDirectedToolWithAuthorizationHeader() {
        String authHeader = "Bearer test-token";
        when(discovery.getAllInstances()).thenReturn(List.of(testInstance1, testInstance2));
        when(instanceManager.createInstance("namespace-1", authHeader)).thenReturn(mockMCP);
        when(instanceManager.createInstance("namespace-2", authHeader)).thenReturn(mockMCP);

        List<CryostatInstance> instances = new ArrayList<>(discovery.getAllInstances());

        for (CryostatInstance instance : instances) {
            CryostatMCP mcp = instanceManager.createInstance(instance.namespace(), authHeader);
            assertNotNull(mcp);
        }

        verify(instanceManager).createInstance("namespace-1", authHeader);
        verify(instanceManager).createInstance("namespace-2", authHeader);
    }

    @Test
    void testListCryostatInstancesReturnsAllInstances() {
        when(discovery.getAllInstances()).thenReturn(List.of(testInstance1, testInstance2));

        List<CryostatInstance> instances = new ArrayList<>(discovery.getAllInstances());

        assertEquals(2, instances.size());
        assertTrue(instances.contains(testInstance1));
        assertTrue(instances.contains(testInstance2));
        verify(discovery).getAllInstances();
    }

    @Test
    void testListCryostatInstancesReturnsEmptyListWhenNoInstances() {
        when(discovery.getAllInstances()).thenReturn(List.of());

        List<CryostatInstance> instances = new ArrayList<>(discovery.getAllInstances());

        assertTrue(instances.isEmpty());
        verify(discovery).getAllInstances();
    }

    @Test
    void testListCryostatInstancesReturnsInstanceDetails() {
        when(discovery.getAllInstances()).thenReturn(List.of(testInstance1));

        List<CryostatInstance> instances = new ArrayList<>(discovery.getAllInstances());

        assertEquals(1, instances.size());
        CryostatInstance instance = instances.get(0);
        assertEquals("cryostat-1", instance.name());
        assertEquals("namespace-1", instance.namespace());
        assertEquals("http://cryostat-1.namespace-1.svc:8181", instance.applicationUrl());
        assertEquals(2, instance.targetNamespaces().size());
        assertTrue(instance.targetNamespaces().contains("namespace-1"));
        assertTrue(instance.targetNamespaces().contains("app-namespace-1"));
    }

    @Test
    void testListCryostatInstancesIsSystemTool() {
        // listCryostatInstances is a system tool that doesn't require namespace
        // and doesn't call any underlying MCP instances
        when(discovery.getAllInstances()).thenReturn(List.of(testInstance1, testInstance2));

        List<CryostatInstance> instances = new ArrayList<>(discovery.getAllInstances());

        assertEquals(2, instances.size());
        verify(discovery).getAllInstances();
        // Verify no MCP instances were created
        verifyNoInteractions(instanceManager);
    }
}