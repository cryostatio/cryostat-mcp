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

import java.util.Set;

import org.junit.jupiter.api.Test;

class CryostatInstanceTest {

    @Test
    void testRecordCreation() {
        CryostatInstance instance =
                new CryostatInstance(
                        "cryostat-1",
                        "cryostat-operator",
                        "https://cryostat-1.cryostat-operator.svc:8181",
                        Set.of("app-namespace-1", "app-namespace-2"));

        assertEquals("cryostat-1", instance.name());
        assertEquals("cryostat-operator", instance.namespace());
        assertEquals("https://cryostat-1.cryostat-operator.svc:8181", instance.applicationUrl());
        assertEquals(2, instance.targetNamespaces().size());
        assertTrue(instance.targetNamespaces().contains("app-namespace-1"));
        assertTrue(instance.targetNamespaces().contains("app-namespace-2"));
    }

    @Test
    void testCompareTo() {
        CryostatInstance instance1 =
                new CryostatInstance(
                        "cryostat-a", "ns1", "https://cryostat-a.ns1.svc:8181", Set.of("app-ns"));
        CryostatInstance instance2 =
                new CryostatInstance(
                        "cryostat-b", "ns2", "https://cryostat-b.ns2.svc:8181", Set.of("app-ns"));
        CryostatInstance instance3 =
                new CryostatInstance(
                        "cryostat-c", "ns3", "https://cryostat-c.ns3.svc:8181", Set.of("app-ns"));

        assertTrue(instance1.compareTo(instance2) < 0);
        assertTrue(instance2.compareTo(instance1) > 0);
        assertTrue(instance2.compareTo(instance3) < 0);
        assertEquals(0, instance1.compareTo(instance1));
    }

    @Test
    void testDeterministicTiebreaker() {
        CryostatInstance instance1 =
                new CryostatInstance(
                        "cryostat-alpha",
                        "ns1",
                        "https://cryostat-alpha.ns1.svc:8181",
                        Set.of("shared-ns"));
        CryostatInstance instance2 =
                new CryostatInstance(
                        "cryostat-beta",
                        "ns2",
                        "https://cryostat-beta.ns2.svc:8181",
                        Set.of("shared-ns"));
        CryostatInstance instance3 =
                new CryostatInstance(
                        "cryostat-gamma",
                        "ns3",
                        "https://cryostat-gamma.ns3.svc:8181",
                        Set.of("shared-ns"));

        assertTrue(instance1.compareTo(instance2) < 0);
        assertTrue(instance1.compareTo(instance3) < 0);
        assertTrue(instance2.compareTo(instance3) < 0);
    }
}
