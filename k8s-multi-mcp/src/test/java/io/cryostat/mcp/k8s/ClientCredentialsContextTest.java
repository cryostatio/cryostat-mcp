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