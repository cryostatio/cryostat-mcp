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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryostatAuthorizationFilterTest {

    private static final URI SECURE_URI = URI.create("https://cryostat.namespace.svc:8181/api/v4");

    @Mock ClientRequestContext requestContext;

    @Test
    void addsSelectedAuthorizationHeader() throws Exception {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        when(requestContext.getHeaders()).thenReturn(headers);
        when(requestContext.getUri()).thenReturn(SECURE_URI);
        CryostatAuthorizationFilter filter =
                new CryostatAuthorizationFilter(() -> "Bearer selected-token");

        filter.filter(requestContext);

        assertEquals("Bearer selected-token", headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void doesNotAddBlankAuthorizationHeader() throws Exception {
        CryostatAuthorizationFilter filter = new CryostatAuthorizationFilter(() -> "   ");

        filter.filter(requestContext);

        verify(requestContext, never()).getHeaders();
    }

    @Test
    void doesNotAddNullAuthorizationHeader() throws Exception {
        CryostatAuthorizationFilter filter = new CryostatAuthorizationFilter(() -> null);

        filter.filter(requestContext);

        verify(requestContext, never()).getHeaders();
    }

    @Test
    void stripsTrailingNewlineFromHeader() throws Exception {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        when(requestContext.getHeaders()).thenReturn(headers);
        when(requestContext.getUri()).thenReturn(SECURE_URI);
        CryostatAuthorizationFilter filter =
                new CryostatAuthorizationFilter(() -> "Bearer token-with-newline\n");

        filter.filter(requestContext);

        assertEquals("Bearer token-with-newline", headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void stripsTrailingCarriageReturnNewlineFromHeader() throws Exception {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        when(requestContext.getHeaders()).thenReturn(headers);
        when(requestContext.getUri()).thenReturn(SECURE_URI);
        CryostatAuthorizationFilter filter =
                new CryostatAuthorizationFilter(() -> "Bearer token-with-crlf\r\n");

        filter.filter(requestContext);

        assertEquals("Bearer token-with-crlf", headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void refusesToSendCredentialsOverRemoteCleartext() {
        when(requestContext.getUri())
                .thenReturn(URI.create("http://cryostat.namespace.svc:8181/api/v4"));
        CryostatAuthorizationFilter filter =
                new CryostatAuthorizationFilter(() -> "Bearer secret-token");

        IOException e = assertThrows(IOException.class, () -> filter.filter(requestContext));

        assertTrue(
                e.getMessage().contains("Refusing to send Authorization credentials"),
                e.getMessage());
        assertFalse(e.getMessage().contains("secret-token"), e.getMessage());
        verify(requestContext, never()).getHeaders();
    }

    @Test
    void sendsCredentialsOverLoopbackCleartext() throws Exception {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        when(requestContext.getHeaders()).thenReturn(headers);
        when(requestContext.getUri()).thenReturn(URI.create("http://localhost:8181/api/v4"));
        CryostatAuthorizationFilter filter =
                new CryostatAuthorizationFilter(() -> "Bearer selected-token");

        filter.filter(requestContext);

        assertEquals("Bearer selected-token", headers.getFirst(HttpHeaders.AUTHORIZATION));
    }
}
