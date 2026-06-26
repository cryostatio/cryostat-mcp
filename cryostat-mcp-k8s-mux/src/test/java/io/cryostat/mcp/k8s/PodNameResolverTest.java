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
        // Note: Without Quarkus, we can't test actual caching behaviour.
        // These tests verify the resolution logic works correctly.
        resolver = new PodNameResolver();
        resolver.log = log;
        resolver.instanceManager = instanceManager;
    }

    // --- resolveTarget: happy path ---

    @Test
    void testResolveTarget_FoundInLiveDataset() {
        String namespace = "test-namespace";
        String podName = "my-app-pod-123";
        Target target = makeTarget(1L, "abc123def456", podName, false);
        DiscoveryNode node = new DiscoveryNode(1L, podName, "Pod", Map.of(), target, null);

        when(instanceManager.createInstance(namespace)).thenReturn(mockMCP);
        when(mockMCP.listTargets(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(List.of("HOST==" + podName)),
                        eq(false)))
                .thenReturn(List.of(node));

        PodNameResolver.TargetInfo result = resolver.resolveTarget(namespace, podName);

        assertEquals(1L, result.targetId());
        assertEquals("abc123def456", result.jvmId());
        // audit log must not be consulted when the live dataset returns a result
        verify(mockMCP, never())
                .listTargets(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(List.of("HOST==" + podName)),
                        eq(true));
    }

    @Test
    void testResolveTarget_FallsBackToAuditLog_WhenNotInLiveDataset() {
        String namespace = "test-namespace";
        String podName = "terminated-pod-456";
        Target target = makeTarget(2L, "xyz789abc012", podName, false);
        DiscoveryNode node = new DiscoveryNode(2L, podName, "Pod", Map.of(), target, null);

        when(instanceManager.createInstance(namespace)).thenReturn(mockMCP);
        when(mockMCP.listTargets(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(List.of("HOST==" + podName)),
                        eq(false)))
                .thenReturn(List.of());
        when(mockMCP.listTargets(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(List.of("HOST==" + podName)),
                        eq(true)))
                .thenReturn(List.of(node));

        PodNameResolver.TargetInfo result = resolver.resolveTarget(namespace, podName);

        assertEquals(2L, result.targetId());
        assertEquals("xyz789abc012", result.jvmId());
        verify(mockMCP)
                .listTargets(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(List.of("HOST==" + podName)),
                        eq(false));
        verify(mockMCP)
                .listTargets(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(List.of("HOST==" + podName)),
                        eq(true));
    }

    // --- resolveTarget: not found ---

    @Test
    void testResolveTarget_NotFoundInEitherDataset_Throws() {
        String namespace = "test-namespace";
        String podName = "non-existent-pod";

        when(instanceManager.createInstance(namespace)).thenReturn(mockMCP);
        when(mockMCP.listTargets(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(List.of("HOST==" + podName)),
                        anyBoolean()))
                .thenReturn(List.of());

        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> resolver.resolveTarget(namespace, podName));

        assertTrue(ex.getMessage().contains("No target found for Pod"));
        assertTrue(ex.getMessage().contains(podName));
        assertTrue(ex.getMessage().contains(namespace));
    }

    @Test
    void testResolveTarget_NotFound_NullReturnTreatedAsEmpty() {
        String namespace = "test-namespace";
        String podName = "non-existent-pod";

        when(instanceManager.createInstance(namespace)).thenReturn(mockMCP);
        when(mockMCP.listTargets(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(List.of("HOST==" + podName)),
                        anyBoolean()))
                .thenReturn(null);

        assertThrows(
                IllegalArgumentException.class, () -> resolver.resolveTarget(namespace, podName));
    }

    // --- resolveTarget: validation ---

    @Test
    void testResolveTarget_NullTargetId_Throws() {
        String namespace = "test-namespace";
        String podName = "my-pod";
        Target target = makeTarget(null, "some-jvm-id", podName, false);
        DiscoveryNode node = new DiscoveryNode(1L, podName, "Pod", Map.of(), target, null);

        when(instanceManager.createInstance(namespace)).thenReturn(mockMCP);
        when(mockMCP.listTargets(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(List.of("HOST==" + podName)),
                        eq(false)))
                .thenReturn(List.of(node));

        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> resolver.resolveTarget(namespace, podName));

        assertTrue(ex.getMessage().contains("Target ID is null"));
    }

    @Test
    void testResolveTarget_NullJvmId_Throws() {
        String namespace = "test-namespace";
        String podName = "my-pod";
        Target target = makeTarget(1L, null, podName, false);
        DiscoveryNode node = new DiscoveryNode(1L, podName, "Pod", Map.of(), target, null);

        when(instanceManager.createInstance(namespace)).thenReturn(mockMCP);
        when(mockMCP.listTargets(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(List.of("HOST==" + podName)),
                        eq(false)))
                .thenReturn(List.of(node));

        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> resolver.resolveTarget(namespace, podName));

        assertTrue(ex.getMessage().contains("JVM ID is null or empty"));
    }

    // --- resolveTarget: agent preference ---

    @Test
    void testResolveTarget_AgentPreferredOverJmx() {
        String namespace = "test-namespace";
        String podName = "my-app-pod";
        Target jmxTarget = makeTarget(1L, "jmx-jvm-id", podName, false);
        Target agentTarget = makeTarget(2L, "agent-jvm-id", podName, true);
        // JMX node listed first; agent node second
        DiscoveryNode jmxNode = new DiscoveryNode(1L, podName, "Pod", Map.of(), jmxTarget, null);
        DiscoveryNode agentNode =
                new DiscoveryNode(2L, podName, "Pod", Map.of(), agentTarget, null);

        when(instanceManager.createInstance(namespace)).thenReturn(mockMCP);
        when(mockMCP.listTargets(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(List.of("HOST==" + podName)),
                        eq(false)))
                .thenReturn(List.of(jmxNode, agentNode));

        PodNameResolver.TargetInfo result = resolver.resolveTarget(namespace, podName);

        assertEquals(2L, result.targetId());
        assertEquals("agent-jvm-id", result.jvmId());
    }

    @Test
    void testResolveTarget_MultipleNodes_LogsDebug() {
        String namespace = "test-namespace";
        String podName = "my-app-pod-123";
        Target t1 = makeTarget(1L, "jvm-id-1", podName, false);
        Target t2 = makeTarget(2L, "jvm-id-2", podName, false);
        DiscoveryNode n1 = new DiscoveryNode(1L, podName, "Pod", Map.of(), t1, null);
        DiscoveryNode n2 = new DiscoveryNode(2L, podName, "Pod", Map.of(), t2, null);

        when(instanceManager.createInstance(namespace)).thenReturn(mockMCP);
        when(mockMCP.listTargets(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(List.of("HOST==" + podName)),
                        eq(false)))
                .thenReturn(List.of(n1, n2));

        resolver.resolveTarget(namespace, podName);

        verify(log).debugf(anyString(), eq(podName), eq(namespace));
    }

    // --- convenience wrappers ---

    @Test
    void testResolvePodNameToJvmId_DelegatesToResolveTarget() {
        String namespace = "test-namespace";
        String podName = "my-app-pod-123";
        Target target = makeTarget(1L, "abc123def456", podName, false);
        DiscoveryNode node = new DiscoveryNode(1L, podName, "Pod", Map.of(), target, null);

        when(instanceManager.createInstance(namespace)).thenReturn(mockMCP);
        when(mockMCP.listTargets(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(List.of("HOST==" + podName)),
                        eq(false)))
                .thenReturn(List.of(node));

        assertEquals("abc123def456", resolver.resolvePodNameToJvmId(namespace, podName));
    }

    @Test
    void testResolvePodNameToTargetId_DelegatesToResolveTarget() {
        String namespace = "test-namespace";
        String podName = "my-app-pod-123";
        Target target = makeTarget(42L, "abc123", podName, false);
        DiscoveryNode node = new DiscoveryNode(1L, podName, "Pod", Map.of(), target, null);

        when(instanceManager.createInstance(namespace)).thenReturn(mockMCP);
        when(mockMCP.listTargets(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(List.of("HOST==" + podName)),
                        eq(false)))
                .thenReturn(List.of(node));

        assertEquals(42L, resolver.resolvePodNameToTargetId(namespace, podName));
    }

    // --- helpers ---

    private static Target makeTarget(Long id, String jvmId, String alias, boolean agent) {
        return new Target(
                id,
                "service:jmx:rmi:///jndi/rmi://" + alias + ":9091/jmxrmi",
                alias,
                jvmId,
                Map.of(),
                new Annotations(Map.of(), Map.of()),
                agent);
    }
}
