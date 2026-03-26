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
package io.cryostat.mcp.k8s.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CryostatModelTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        mapper = new ObjectMapper();
    }

    @Test
    void testCryostatSpecSerialization() throws Exception {
        CryostatSpec spec = new CryostatSpec();
        spec.setTargetNamespaces(List.of("ns1", "ns2", "ns3"));

        String json = mapper.writeValueAsString(spec);
        assertTrue(json.contains("\"targetNamespaces\""));
    }

    @Test
    void testCryostatSpecDeserialization() throws Exception {
        String json =
                """
                {
                    "targetNamespaces": ["app-ns-1", "app-ns-2"]
                }
                """;

        CryostatSpec spec = mapper.readValue(json, CryostatSpec.class);
        assertNotNull(spec);
        assertEquals(2, spec.getTargetNamespaces().size());
        assertTrue(spec.getTargetNamespaces().contains("app-ns-1"));
        assertTrue(spec.getTargetNamespaces().contains("app-ns-2"));
    }

    @Test
    void testCryostatSpecDeserializationWithUnknownFields() throws Exception {
        String json =
                """
                {
                    "targetNamespaces": ["app-ns-1"],
                    "enableCertManager": true,
                    "unknownField": "should be ignored",
                    "anotherUnknownField": 12345
                }
                """;

        CryostatSpec spec = mapper.readValue(json, CryostatSpec.class);
        assertNotNull(spec);
        assertEquals(1, spec.getTargetNamespaces().size());
        assertEquals("app-ns-1", spec.getTargetNamespaces().get(0));
    }

    @Test
    void testCryostatStatusSerialization() throws Exception {
        CryostatStatus status = new CryostatStatus();
        status.setApplicationUrl("https://cryostat.example.com");

        String json = mapper.writeValueAsString(status);
        assertTrue(json.contains("\"applicationUrl\""));
        assertTrue(json.contains("https://cryostat.example.com"));
    }

    @Test
    void testCryostatStatusDeserialization() throws Exception {
        String json =
                """
                {
                    "applicationUrl": "https://cryostat-test.apps.cluster.example.com"
                }
                """;

        CryostatStatus status = mapper.readValue(json, CryostatStatus.class);
        assertNotNull(status);
        assertEquals("https://cryostat-test.apps.cluster.example.com", status.getApplicationUrl());
    }

    @Test
    void testCryostatCRCreation() {
        Cryostat cr = new Cryostat();
        cr.setMetadata(
                new ObjectMetaBuilder()
                        .withName("cryostat-sample")
                        .withNamespace("cryostat-operator")
                        .build());

        CryostatSpec spec = new CryostatSpec();
        spec.setTargetNamespaces(List.of("app-namespace"));
        cr.setSpec(spec);

        CryostatStatus status = new CryostatStatus();
        status.setApplicationUrl("https://cryostat-sample.cryostat-operator.svc:8181");
        cr.setStatus(status);

        assertEquals("cryostat-sample", cr.getMetadata().getName());
        assertEquals("cryostat-operator", cr.getMetadata().getNamespace());
        assertEquals(1, cr.getSpec().getTargetNamespaces().size());
        assertEquals(
                "https://cryostat-sample.cryostat-operator.svc:8181",
                cr.getStatus().getApplicationUrl());
    }

    @Test
    void testCryostatCRWithNullSpec() {
        Cryostat cr = new Cryostat();
        cr.setMetadata(
                new ObjectMetaBuilder()
                        .withName("cryostat-minimal")
                        .withNamespace("default")
                        .build());

        assertNotNull(cr.getMetadata());
        assertNotNull(cr.getSpec());
    }

    @Test
    void testCryostatSpecWithNullTargetNamespaces() {
        CryostatSpec spec = new CryostatSpec();
        assertNull(spec.getTargetNamespaces());

        spec.setTargetNamespaces(null);
        assertNull(spec.getTargetNamespaces());
    }
}
