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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import io.cryostat.mcp.CryostatMCP;
import io.cryostat.mcp.model.graphql.Annotations;
import io.cryostat.mcp.model.graphql.DiscoveryNode;
import io.cryostat.mcp.model.graphql.Target;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PodNameResolverTest {

    private PodNameResolver resolver;

    @Mock private Logger log;

    @Mock private CryostatMCPInstanceManager instanceManager;

    private CryostatMCP mockMCP;

    @BeforeEach
    void setUp() {
        mockMCP = mock(CryostatMCP.class);
        // Note: Without Quarkus, we can't test actual caching behavior
        // These tests verify the resolution logic works correctly
        resolver = new PodNameResolver();
        resolver.log = log;
        resolver.instanceManager = instanceManager;
    }

    @Test
    void testResolvePodNameToJvmId_Success() {
        // Arrange
        String namespace = "test-namespace";
        String podName = "my-app-pod-123";
        String expectedJvmId = "abc123def456";

        Target target =
                new Target(
                        1L,
                        "service:jmx:rmi:///jndi/rmi://my-app-pod-123:9091/jmxrmi",
                        "my-app",
                        expectedJvmId,
                        Map.of(),
                        new Annotations(Map.of(), Map.of()));

        DiscoveryNode node = new DiscoveryNode(1L, podName, "Pod", Map.of(), target);

        when(instanceManager.createInstance(namespace)).thenReturn(mockMCP);
        when(mockMCP.listTargets(isNull(), isNull(), eq(List.of(podName)), isNull(), eq(false)))
                .thenReturn(List.of(node));

        // Act
        String result = resolver.resolvePodNameToJvmId(namespace, podName);

        // Assert
        assertEquals(expectedJvmId, result);
        verify(instanceManager).createInstance(namespace);
        verify(mockMCP).listTargets(isNull(), isNull(), eq(List.of(podName)), isNull(), eq(false));
    }

    @Test
    void testResolvePodNameToJvmId_WithAuditLog() {
        // Arrange
        String namespace = "test-namespace";
        String podName = "terminated-pod-456";
        String expectedJvmId = "xyz789abc012";

        Target target =
                new Target(
                        2L,
                        "service:jmx:rmi:///jndi/rmi://terminated-pod-456:9091/jmxrmi",
                        "terminated-app",
                        expectedJvmId,
                        Map.of(),
                        new Annotations(Map.of(), Map.of()));

        DiscoveryNode node = new DiscoveryNode(2L, podName, "Pod", Map.of(), target);

        when(instanceManager.createInstance(namespace)).thenReturn(mockMCP);
        when(mockMCP.listTargets(isNull(), isNull(), eq(List.of(podName)), isNull(), eq(true)))
                .thenReturn(List.of(node));

        // Act
        String result = resolver.resolvePodNameToJvmId(namespace, podName, true);

        // Assert
        assertEquals(expectedJvmId, result);
        verify(mockMCP).listTargets(isNull(), isNull(), eq(List.of(podName)), isNull(), eq(true));
    }

    @Test
    void testResolvePodNameToTargetId_Success() {
        // Arrange
        String namespace = "test-namespace";
        String podName = "my-app-pod-123";
        Long expectedTargetId = 42L;

        Target target =
                new Target(
                        expectedTargetId,
                        "service:jmx:rmi:///jndi/rmi://my-app-pod-123:9091/jmxrmi",
                        "my-app",
                        "abc123",
                        Map.of(),
                        new Annotations(Map.of(), Map.of()));

        DiscoveryNode node = new DiscoveryNode(1L, podName, "Pod", Map.of(), target);

        when(instanceManager.createInstance(namespace)).thenReturn(mockMCP);
        when(mockMCP.listTargets(isNull(), isNull(), eq(List.of(podName)), isNull(), eq(false)))
                .thenReturn(List.of(node));

        // Act
        Long result = resolver.resolvePodNameToTargetId(namespace, podName);

        // Assert
        assertEquals(expectedTargetId, result);
    }

    @Test
    void testResolvePodNameToJvmId_NotFound() {
        // Arrange
        String namespace = "test-namespace";
        String podName = "non-existent-pod";

        when(instanceManager.createInstance(namespace)).thenReturn(mockMCP);
        when(mockMCP.listTargets(isNull(), isNull(), eq(List.of(podName)), isNull(), eq(false)))
                .thenReturn(List.of());

        // Act & Assert
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> resolver.resolvePodNameToJvmId(namespace, podName));

        assertTrue(exception.getMessage().contains("No target found for Pod"));
        assertTrue(exception.getMessage().contains(podName));
        assertTrue(exception.getMessage().contains(namespace));
    }

    @Test
    void testResolvePodNameToJvmId_NotFound_SuggestsAuditLog() {
        // Arrange
        String namespace = "test-namespace";
        String podName = "non-existent-pod";

        when(instanceManager.createInstance(namespace)).thenReturn(mockMCP);
        when(mockMCP.listTargets(isNull(), isNull(), eq(List.of(podName)), isNull(), eq(false)))
                .thenReturn(List.of());

        // Act & Assert
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> resolver.resolvePodNameToJvmId(namespace, podName, false));

        assertTrue(exception.getMessage().contains("try with useAuditLog=true"));
    }

    @Test
    void testResolvePodNameToJvmId_NotFound_WithAuditLog_NoSuggestion() {
        // Arrange
        String namespace = "test-namespace";
        String podName = "non-existent-pod";

        when(instanceManager.createInstance(namespace)).thenReturn(mockMCP);
        when(mockMCP.listTargets(isNull(), isNull(), eq(List.of(podName)), isNull(), eq(true)))
                .thenReturn(List.of());

        // Act & Assert
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> resolver.resolvePodNameToJvmId(namespace, podName, true));

        assertFalse(exception.getMessage().contains("try with useAuditLog=true"));
    }

    @Test
    void testMultipleTargetsWarning() {
        // Arrange
        String namespace = "test-namespace";
        String podName = "my-app-pod-123";
        String expectedJvmId = "abc123def456";

        Target target1 =
                new Target(
                        1L,
                        "service:jmx:rmi:///jndi/rmi://my-app-pod-123:9091/jmxrmi",
                        "my-app",
                        expectedJvmId,
                        Map.of(),
                        new Annotations(Map.of(), Map.of()));

        Target target2 =
                new Target(
                        2L,
                        "service:jmx:rmi:///jndi/rmi://my-app-pod-123:9092/jmxrmi",
                        "my-app-2",
                        "different-jvm-id",
                        Map.of(),
                        new Annotations(Map.of(), Map.of()));

        DiscoveryNode node1 = new DiscoveryNode(1L, podName, "Pod", Map.of(), target1);
        DiscoveryNode node2 = new DiscoveryNode(2L, podName, "Pod", Map.of(), target2);

        when(instanceManager.createInstance(namespace)).thenReturn(mockMCP);
        when(mockMCP.listTargets(isNull(), isNull(), eq(List.of(podName)), isNull(), eq(false)))
                .thenReturn(List.of(node1, node2));

        // Act - Should use first target and log warning
        String result = resolver.resolvePodNameToJvmId(namespace, podName);

        // Assert - Should return first target's JVM ID
        assertEquals(expectedJvmId, result);
    }

    @Test
    void testResolvePodNameToTargetId_WithAuditLog() {
        // Arrange
        String namespace = "test-namespace";
        String podName = "terminated-pod-789";
        Long expectedTargetId = 99L;

        Target target =
                new Target(
                        expectedTargetId,
                        "service:jmx:rmi:///jndi/rmi://terminated-pod-789:9091/jmxrmi",
                        "terminated-app",
                        "historical-jvm-id",
                        Map.of(),
                        new Annotations(Map.of(), Map.of()));

        DiscoveryNode node = new DiscoveryNode(3L, podName, "Pod", Map.of(), target);

        when(instanceManager.createInstance(namespace)).thenReturn(mockMCP);
        when(mockMCP.listTargets(isNull(), isNull(), eq(List.of(podName)), isNull(), eq(true)))
                .thenReturn(List.of(node));

        // Act
        Long result = resolver.resolvePodNameToTargetId(namespace, podName, true);

        // Assert
        assertEquals(expectedTargetId, result);
        verify(mockMCP).listTargets(isNull(), isNull(), eq(List.of(podName)), isNull(), eq(true));
    }
}
