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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientCredentialsContextTest {

    private ClientCredentialsContext context;

    @BeforeEach
    void setUp() {
        context = new ClientCredentialsContext();
    }

    @Test
    void testInitialState() {
        assertNull(context.getAuthorizationHeader());
        assertFalse(context.hasCredentials());
    }

    @Test
    void testSetAndGetAuthorizationHeader() {
        String authHeader = "Bearer test-token-123";
        context.setAuthorizationHeader(authHeader);

        assertEquals(authHeader, context.getAuthorizationHeader());
        assertTrue(context.hasCredentials());
    }

    @Test
    void testHasCredentialsWithEmptyString() {
        context.setAuthorizationHeader("");
        assertFalse(context.hasCredentials());
    }

    @Test
    void testHasCredentialsWithNull() {
        context.setAuthorizationHeader(null);
        assertFalse(context.hasCredentials());
    }

    @Test
    void testOverwriteCredentials() {
        context.setAuthorizationHeader("Bearer old-token");
        context.setAuthorizationHeader("Bearer new-token");

        assertEquals("Bearer new-token", context.getAuthorizationHeader());
        assertTrue(context.hasCredentials());
    }

    @Test
    void testClearCredentials() {
        context.setAuthorizationHeader("Bearer test-token");
        assertTrue(context.hasCredentials());

        context.setAuthorizationHeader(null);
        assertFalse(context.hasCredentials());
        assertNull(context.getAuthorizationHeader());
    }
}
