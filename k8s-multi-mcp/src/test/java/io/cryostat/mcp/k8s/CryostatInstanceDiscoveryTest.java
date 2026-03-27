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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.quarkus.runtime.StartupEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryostatInstanceDiscoveryTest {

    @Mock KubernetesClient k8sClient;

    @Mock
    @SuppressWarnings("rawtypes")
    MixedOperation serviceOperation;

    @Mock
    @SuppressWarnings("rawtypes")
    FilterWatchListDeletable serviceFilterable;

    @InjectMocks CryostatInstanceDiscovery discovery;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        when(k8sClient.services()).thenReturn(serviceOperation);
        when(serviceOperation.inAnyNamespace()).thenReturn(serviceOperation);
        when(serviceOperation.withLabel(anyString(), anyString())).thenReturn(serviceFilterable);
        when(serviceFilterable.withLabel(anyString(), anyString())).thenReturn(serviceFilterable);
    }

    @Test
    void testFindByNamespaceWithSingleMatch() throws Exception {
        Service svc = createService("cryostat-1", "cryostat-ns", 8181, "https");

        when(serviceFilterable.list()).thenReturn(createServiceList(svc));
        when(serviceFilterable.watch(any())).thenReturn(null);

        discovery.onStart(mock(StartupEvent.class));
        waitForDiscovery();

        Optional<CryostatInstance> result = discovery.findByNamespace("cryostat-ns");

        assertTrue(result.isPresent());
        assertEquals("cryostat-1", result.get().name());
        assertEquals("cryostat-ns", result.get().namespace());
    }

    @Test
    void testFindByNamespaceWithMultipleMatches() throws Exception {
        Service svc1 = createService("cryostat-beta", "ns1", 8181, "https");
        Service svc2 = createService("cryostat-alpha", "ns2", 8181, "https");
        Service svc3 = createService("cryostat-gamma", "ns3", 8181, "https");

        when(serviceFilterable.list()).thenReturn(createServiceList(svc1, svc2, svc3));
        when(serviceFilterable.watch(any())).thenReturn(null);

        discovery.onStart(mock(StartupEvent.class));
        waitForDiscovery();

        // All services default to monitoring their own namespace
        Optional<CryostatInstance> result1 = discovery.findByNamespace("ns1");
        Optional<CryostatInstance> result2 = discovery.findByNamespace("ns2");
        Optional<CryostatInstance> result3 = discovery.findByNamespace("ns3");

        assertTrue(result1.isPresent());
        assertEquals("cryostat-beta", result1.get().name());

        assertTrue(result2.isPresent());
        assertEquals("cryostat-alpha", result2.get().name());

        assertTrue(result3.isPresent());
        assertEquals("cryostat-gamma", result3.get().name());
    }

    @Test
    void testFindByNamespaceWithNoMatch() throws Exception {
        Service svc = createService("cryostat-1", "cryostat-ns", 8181, "https");

        when(serviceFilterable.list()).thenReturn(createServiceList(svc));
        when(serviceFilterable.watch(any())).thenReturn(null);

        discovery.onStart(mock(StartupEvent.class));
        waitForDiscovery();

        Optional<CryostatInstance> result = discovery.findByNamespace("non-existent-ns");

        assertFalse(result.isPresent());
    }

    @Test
    void testGetAllInstances() throws Exception {
        Service svc1 = createService("cryostat-1", "ns1", 8181, "https");
        Service svc2 = createService("cryostat-2", "ns2", 8181, "https");

        when(serviceFilterable.list()).thenReturn(createServiceList(svc1, svc2));
        when(serviceFilterable.watch(any())).thenReturn(null);

        discovery.onStart(mock(StartupEvent.class));
        waitForDiscovery();

        Collection<CryostatInstance> instances = discovery.getAllInstances();

        assertEquals(2, instances.size());
    }

    @Test
    void testGetNamespaceMapping() throws Exception {
        Service svc1 = createService("cryostat-1", "ns1", 8181, "https");
        Service svc2 = createService("cryostat-2", "ns2", 8181, "https");

        when(serviceFilterable.list()).thenReturn(createServiceList(svc1, svc2));
        when(serviceFilterable.watch(any())).thenReturn(null);

        discovery.onStart(mock(StartupEvent.class));
        waitForDiscovery();

        Map<String, List<CryostatInstance>> mapping = discovery.getNamespaceMapping();

        assertEquals(2, mapping.size());
        assertTrue(mapping.containsKey("ns1"));
        assertTrue(mapping.containsKey("ns2"));

        assertEquals(1, mapping.get("ns1").size());
        assertEquals(1, mapping.get("ns2").size());
    }

    @Test
    void testServiceWithHttpsAppProtocol() throws Exception {
        Service svc = createService("cryostat-1", "cryostat-ns", 8181, "https");

        when(serviceFilterable.list()).thenReturn(createServiceList(svc));
        when(serviceFilterable.watch(any())).thenReturn(null);

        discovery.onStart(mock(StartupEvent.class));
        waitForDiscovery();

        Optional<CryostatInstance> result = discovery.findByNamespace("cryostat-ns");

        assertTrue(result.isPresent());
        assertEquals("https://cryostat-1.cryostat-ns.svc:8181", result.get().applicationUrl());
    }

    @Test
    void testServiceWithHttpAppProtocol() throws Exception {
        Service svc = createService("cryostat-1", "cryostat-ns", 8080, "http");

        when(serviceFilterable.list()).thenReturn(createServiceList(svc));
        when(serviceFilterable.watch(any())).thenReturn(null);

        discovery.onStart(mock(StartupEvent.class));
        waitForDiscovery();

        Optional<CryostatInstance> result = discovery.findByNamespace("cryostat-ns");

        assertTrue(result.isPresent());
        assertEquals("http://cryostat-1.cryostat-ns.svc:8080", result.get().applicationUrl());
    }

    @Test
    void testServiceWithoutRequiredLabelsIsIgnored() throws Exception {
        Service svc =
                new ServiceBuilder()
                        .withNewMetadata()
                        .withName("not-cryostat")
                        .withNamespace("test-ns")
                        .addToLabels("some-label", "some-value")
                        .endMetadata()
                        .withNewSpec()
                        .withPorts(
                                new ServicePortBuilder()
                                        .withPort(8181)
                                        .withAppProtocol("https")
                                        .build())
                        .endSpec()
                        .build();

        when(serviceFilterable.list()).thenReturn(createServiceList(svc));
        when(serviceFilterable.watch(any())).thenReturn(null);

        discovery.onStart(mock(StartupEvent.class));
        waitForDiscovery();

        Collection<CryostatInstance> instances = discovery.getAllInstances();

        assertEquals(0, instances.size());
    }

    private void waitForDiscovery() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(100);
    }

    private Service createService(String name, String namespace, int port, String protocol) {
        return new ServiceBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .addToLabels("app.kubernetes.io/part-of", "cryostat")
                .addToLabels("app.kubernetes.io/component", "cryostat")
                .endMetadata()
                .withNewSpec()
                .withPorts(
                        new ServicePortBuilder().withPort(port).withAppProtocol(protocol).build())
                .endSpec()
                .build();
    }

    private ServiceList createServiceList(Service... services) {
        ServiceList list = new ServiceList();
        list.setItems(List.of(services));
        return list;
    }
}