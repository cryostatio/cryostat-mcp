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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryostatAuthorizationTest {

    @Mock Instance<RoutingContext> routingContexts;
    @Mock RoutingContext routingContext;
    @Mock HttpServerRequest request;

    private CryostatAuthorization authorization;

    @BeforeEach
    void setUp() {
        authorization = new CryostatAuthorization();
        authorization.routingContexts = routingContexts;
    }

    @Test
    void returnsCryostatAuthorizationHeaderFromCurrentRequest() {
        String expected = "Bearer per-invocation-token";
        when(routingContexts.isResolvable()).thenReturn(true);
        when(routingContexts.get()).thenReturn(routingContext);
        when(routingContext.request()).thenReturn(request);
        when(request.getHeader(CryostatAuthorization.PASSTHROUGH_HEADER)).thenReturn(expected);

        assertEquals(expected, authorization.getPassthroughAuthorizationHeader());
    }

    @Test
    void stripsTrailingNewlineFromPassthroughHeader() {
        when(routingContexts.isResolvable()).thenReturn(true);
        when(routingContexts.get()).thenReturn(routingContext);
        when(routingContext.request()).thenReturn(request);
        when(request.getHeader(CryostatAuthorization.PASSTHROUGH_HEADER))
                .thenReturn("Bearer per-invocation-token\n");

        assertEquals(
                "Bearer per-invocation-token", authorization.getPassthroughAuthorizationHeader());
    }

    @Test
    void stripsTrailingCarriageReturnNewlineFromPassthroughHeader() {
        when(routingContexts.isResolvable()).thenReturn(true);
        when(routingContexts.get()).thenReturn(routingContext);
        when(routingContext.request()).thenReturn(request);
        when(request.getHeader(CryostatAuthorization.PASSTHROUGH_HEADER))
                .thenReturn("Bearer per-invocation-token\r\n");

        assertEquals(
                "Bearer per-invocation-token", authorization.getPassthroughAuthorizationHeader());
    }

    @Test
    void ignoresBlankCryostatAuthorizationHeader() {
        when(routingContexts.isResolvable()).thenReturn(true);
        when(routingContexts.get()).thenReturn(routingContext);
        when(routingContext.request()).thenReturn(request);
        when(request.getHeader(CryostatAuthorization.PASSTHROUGH_HEADER)).thenReturn("   ");

        assertNull(authorization.getPassthroughAuthorizationHeader());
    }

    @Test
    void doesNotUseTheStandardAuthorizationHeader() {
        when(routingContexts.isResolvable()).thenReturn(true);
        when(routingContexts.get()).thenReturn(routingContext);
        when(routingContext.request()).thenReturn(request);
        when(request.getHeader(CryostatAuthorization.PASSTHROUGH_HEADER)).thenReturn(null);

        assertNull(authorization.getPassthroughAuthorizationHeader());
        verify(request, never()).getHeader("Authorization");
    }

    @Test
    void returnsNoHeaderOutsideAnHttpRequestContext() {
        when(routingContexts.isResolvable()).thenReturn(true);
        when(routingContexts.get()).thenThrow(new ContextNotActiveException());

        assertNull(authorization.getPassthroughAuthorizationHeader());
    }

    @Test
    void propagatesCapturedHeaderOutsideAnHttpRequestContext() {
        when(routingContexts.isResolvable()).thenReturn(false);

        String result =
                authorization.withPassthroughAuthorizationHeader(
                        "Bearer captured-token", authorization::getPassthroughAuthorizationHeader);

        assertEquals("Bearer captured-token", result);
        assertNull(authorization.getPassthroughAuthorizationHeader());
    }

    @Test
    void restoresOuterHeaderAfterNestedPropagation() {
        when(routingContexts.isResolvable()).thenReturn(false);

        String result =
                authorization.withPassthroughAuthorizationHeader(
                        "Bearer outer-token",
                        () -> {
                            assertEquals(
                                    "Bearer inner-token",
                                    authorization.withPassthroughAuthorizationHeader(
                                            "Bearer inner-token",
                                            authorization::getPassthroughAuthorizationHeader));
                            return authorization.getPassthroughAuthorizationHeader();
                        });

        assertEquals("Bearer outer-token", result);
        assertNull(authorization.getPassthroughAuthorizationHeader());
    }

    @Test
    void clearsPropagatedHeaderWhenActionThrows() {
        when(routingContexts.isResolvable()).thenReturn(false);

        assertThrows(
                IllegalStateException.class,
                () ->
                        authorization.withPassthroughAuthorizationHeader(
                                "Bearer captured-token",
                                () -> {
                                    throw new IllegalStateException("expected");
                                }));

        assertNull(authorization.getPassthroughAuthorizationHeader());
    }
}
