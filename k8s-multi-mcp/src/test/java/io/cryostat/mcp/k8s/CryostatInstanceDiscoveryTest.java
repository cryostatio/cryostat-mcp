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
import static org.mockito.Mockito.*;

import io.cryostat.mcp.k8s.model.Cryostat;
import io.cryostat.mcp.k8s.model.CryostatSpec;
import io.cryostat.mcp.k8s.model.CryostatStatus;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import io.quarkus.runtime.StartupEvent;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    MixedOperation cryostatOperation;

    @Mock
    @SuppressWarnings("rawtypes")
    NonNamespaceOperation cryostatNonNamespaceOperation;

    @Mock Resource<Cryostat> cryostatResource;

    @Mock
    @SuppressWarnings("rawtypes")
    MixedOperation serviceOperation;

    @Mock
    @SuppressWarnings("rawtypes")
    NonNamespaceOperation serviceNonNamespaceOperation;

    @Mock ServiceResource<Service> serviceResource;

    @InjectMocks CryostatInstanceDiscovery discovery;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        lenient().when(k8sClient.resources(Cryostat.class)).thenReturn(cryostatOperation);
        lenient()
                .when(cryostatOperation.inAnyNamespace())
                .thenReturn(cryostatNonNamespaceOperation);
        lenient().when(k8sClient.services()).thenReturn(serviceOperation);
    }

    @Test
    void testFindByNamespaceWithSingleMatch() {
        Cryostat cr = createCryostat("cryostat-1", "cryostat-ns", List.of("app-ns"));

        when(cryostatNonNamespaceOperation.list()).thenReturn(createCryostatList(cr));
        when(cryostatNonNamespaceOperation.watch(any())).thenReturn(null);

        discovery.onStart(mock(StartupEvent.class));

        Optional<CryostatInstance> result = discovery.findByNamespace("app-ns");

        assertTrue(result.isPresent());
        assertEquals("cryostat-1", result.get().name());
        assertEquals("cryostat-ns", result.get().namespace());
    }

    @Test
    void testFindByNamespaceWithMultipleMatches() {
        Cryostat cr1 = createCryostat("cryostat-beta", "ns1", List.of("shared-ns"));
        Cryostat cr2 = createCryostat("cryostat-alpha", "ns2", List.of("shared-ns"));
        Cryostat cr3 = createCryostat("cryostat-gamma", "ns3", List.of("shared-ns"));

        when(cryostatNonNamespaceOperation.list())
                .thenReturn(createCryostatList(cr1, cr2, cr3));
        when(cryostatNonNamespaceOperation.watch(any())).thenReturn(null);

        discovery.onStart(mock(StartupEvent.class));

        Optional<CryostatInstance> result = discovery.findByNamespace("shared-ns");

        assertTrue(result.isPresent());
        assertEquals("cryostat-alpha", result.get().name());
    }

    @Test
    void testFindByNamespaceWithNoMatch() {
        Cryostat cr = createCryostat("cryostat-1", "cryostat-ns", List.of("app-ns"));

        when(cryostatNonNamespaceOperation.list()).thenReturn(createCryostatList(cr));
        when(cryostatNonNamespaceOperation.watch(any())).thenReturn(null);

        discovery.onStart(mock(StartupEvent.class));

        Optional<CryostatInstance> result = discovery.findByNamespace("non-existent-ns");

        assertFalse(result.isPresent());
    }

    @Test
    void testFindAllByNamespace() {
        Cryostat cr1 = createCryostat("cryostat-1", "ns1", List.of("shared-ns"));
        Cryostat cr2 = createCryostat("cryostat-2", "ns2", List.of("shared-ns"));

        when(cryostatNonNamespaceOperation.list()).thenReturn(createCryostatList(cr1, cr2));
        when(cryostatNonNamespaceOperation.watch(any())).thenReturn(null);

        discovery.onStart(mock(StartupEvent.class));

        List<CryostatInstance> results = discovery.findAllByNamespace("shared-ns");

        assertEquals(2, results.size());
        assertEquals("cryostat-1", results.get(0).name());
        assertEquals("cryostat-2", results.get(1).name());
    }

    @Test
    void testGetAllInstances() {
        Cryostat cr1 = createCryostat("cryostat-1", "ns1", List.of("app-ns-1"));
        Cryostat cr2 = createCryostat("cryostat-2", "ns2", List.of("app-ns-2"));

        when(cryostatNonNamespaceOperation.list()).thenReturn(createCryostatList(cr1, cr2));
        when(cryostatNonNamespaceOperation.watch(any())).thenReturn(null);

        discovery.onStart(mock(StartupEvent.class));

        Collection<CryostatInstance> instances = discovery.getAllInstances();

        assertEquals(2, instances.size());
    }

    @Test
    void testGetNamespaceMapping() {
        Cryostat cr1 = createCryostat("cryostat-1", "ns1", List.of("app-ns-1", "app-ns-2"));
        Cryostat cr2 = createCryostat("cryostat-2", "ns2", List.of("app-ns-2", "app-ns-3"));

        when(cryostatNonNamespaceOperation.list()).thenReturn(createCryostatList(cr1, cr2));
        when(cryostatNonNamespaceOperation.watch(any())).thenReturn(null);

        discovery.onStart(mock(StartupEvent.class));

        Map<String, List<CryostatInstance>> mapping = discovery.getNamespaceMapping();

        assertEquals(3, mapping.size());
        assertTrue(mapping.containsKey("app-ns-1"));
        assertTrue(mapping.containsKey("app-ns-2"));
        assertTrue(mapping.containsKey("app-ns-3"));

        assertEquals(1, mapping.get("app-ns-1").size());
        assertEquals(2, mapping.get("app-ns-2").size());
        assertEquals(1, mapping.get("app-ns-3").size());
    }

    @Test
    void testCryostatWithNullTargetNamespacesDefaultsToOwnNamespace() {
        Cryostat cr = createCryostat("cryostat-1", "cryostat-ns", null);

        when(cryostatNonNamespaceOperation.list()).thenReturn(createCryostatList(cr));
        when(cryostatNonNamespaceOperation.watch(any())).thenReturn(null);

        discovery.onStart(mock(StartupEvent.class));

        Optional<CryostatInstance> result = discovery.findByNamespace("cryostat-ns");

        assertTrue(result.isPresent());
        assertEquals("cryostat-1", result.get().name());
        assertTrue(result.get().targetNamespaces().contains("cryostat-ns"));
    }

    @Test
    void testCryostatWithEmptyTargetNamespacesDefaultsToOwnNamespace() {
        Cryostat cr = createCryostat("cryostat-1", "cryostat-ns", List.of());

        when(cryostatNonNamespaceOperation.list()).thenReturn(createCryostatList(cr));
        when(cryostatNonNamespaceOperation.watch(any())).thenReturn(null);

        discovery.onStart(mock(StartupEvent.class));

        Optional<CryostatInstance> result = discovery.findByNamespace("cryostat-ns");

        assertTrue(result.isPresent());
        assertEquals("cryostat-1", result.get().name());
        assertTrue(result.get().targetNamespaces().contains("cryostat-ns"));
    }

    @Test
    void testApplicationUrlFromStatus() {
        Cryostat cr = createCryostat("cryostat-1", "cryostat-ns", List.of("app-ns"));
        CryostatStatus status = new CryostatStatus();
        status.setApplicationUrl("https://cryostat-1.apps.cluster.example.com");
        cr.setStatus(status);

        when(cryostatNonNamespaceOperation.list()).thenReturn(createCryostatList(cr));
        when(cryostatNonNamespaceOperation.watch(any())).thenReturn(null);

        discovery.onStart(mock(StartupEvent.class));

        Optional<CryostatInstance> result = discovery.findByNamespace("app-ns");

        assertTrue(result.isPresent());
        assertEquals("https://cryostat-1.apps.cluster.example.com", result.get().applicationUrl());
    }

    private Cryostat createCryostat(String name, String namespace, List<String> targetNamespaces) {
        Cryostat cr = new Cryostat();
        cr.setMetadata(new ObjectMetaBuilder().withName(name).withNamespace(namespace).build());

        CryostatSpec spec = new CryostatSpec();
        spec.setTargetNamespaces(targetNamespaces);
        cr.setSpec(spec);

        return cr;
    }

    private io.fabric8.kubernetes.api.model.KubernetesResourceList<Cryostat> createCryostatList(
            Cryostat... crs) {
        return new io.fabric8.kubernetes.api.model.KubernetesResourceList<Cryostat>() {
            @Override
            public List<Cryostat> getItems() {
                return List.of(crs);
            }

            @Override
            public io.fabric8.kubernetes.api.model.ListMeta getMetadata() {
                return null;
            }
        };
    }

    private Service createService(String name, String namespace, int port, String protocol) {
        return new ServiceBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withPorts(
                        new ServicePortBuilder()
                                .withPort(port)
                                .withAppProtocol(protocol)
                                .build())
                .endSpec()
                .build();
    }
}