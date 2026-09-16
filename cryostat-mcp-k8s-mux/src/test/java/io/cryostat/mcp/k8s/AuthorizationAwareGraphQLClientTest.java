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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import io.cryostat.mcp.model.ActiveRecordingsFilter;
import io.cryostat.mcp.model.DiscoveryNodeFilter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizationAwareGraphQLClientTest {

    private static final URI SECURE_ENDPOINT =
            URI.create("https://cryostat.namespace.svc:8181/api/v4/graphql");

    @Mock AuthorizationAwareGraphQLClient.Delegate delegate;
    @Mock DiscoveryNodeFilter filter;
    @Mock ActiveRecordingsFilter recordingsFilter;

    @Test
    void suppliesCurrentAuthorizationHeaderForEachInvocation() {
        AtomicReference<String> authorizationHeader =
                new AtomicReference<>("Bearer per-invocation-token");
        AuthorizationAwareGraphQLClient client =
                new AuthorizationAwareGraphQLClient(
                        delegate, authorizationHeader::get, SECURE_ENDPOINT);

        client.targetNodes(filter, false);

        verify(delegate).targetNodes(filter, false, "Bearer per-invocation-token");

        authorizationHeader.set(null);
        client.environmentNodes(filter);

        verify(delegate).environmentNodes(filter, null);

        authorizationHeader.set("Bearer static-token");
        client.targetNodes(filter, recordingsFilter);

        verify(delegate).targetNodes(filter, recordingsFilter, "Bearer static-token");
    }

    @Test
    void stripsTrailingNewlineFromHeaderForTargetNodes() {
        AuthorizationAwareGraphQLClient client =
                new AuthorizationAwareGraphQLClient(
                        delegate, () -> "Bearer token-with-newline\n", SECURE_ENDPOINT);

        client.targetNodes(filter, false);

        verify(delegate).targetNodes(filter, false, "Bearer token-with-newline");
    }

    @Test
    void stripsTrailingCarriageReturnNewlineFromHeaderForEnvironmentNodes() {
        AuthorizationAwareGraphQLClient client =
                new AuthorizationAwareGraphQLClient(
                        delegate, () -> "Bearer token-with-crlf\r\n", SECURE_ENDPOINT);

        client.environmentNodes(filter);

        verify(delegate).environmentNodes(filter, "Bearer token-with-crlf");
    }

    @Test
    void stripsTrailingNewlineFromHeaderForTargetNodesWithRecordingsFilter() {
        AuthorizationAwareGraphQLClient client =
                new AuthorizationAwareGraphQLClient(
                        delegate, () -> "Bearer token-with-newline\n", SECURE_ENDPOINT);

        client.targetNodes(filter, recordingsFilter);

        verify(delegate).targetNodes(filter, recordingsFilter, "Bearer token-with-newline");
    }

    @Test
    void passesNullToAllDelegateMethodsWhenSupplierReturnsNull() {
        AuthorizationAwareGraphQLClient client =
                new AuthorizationAwareGraphQLClient(delegate, () -> null, SECURE_ENDPOINT);

        client.targetNodes(filter, false);
        client.environmentNodes(filter);
        client.targetNodes(filter, recordingsFilter);

        verify(delegate).targetNodes(filter, false, null);
        verify(delegate).environmentNodes(filter, null);
        verify(delegate).targetNodes(filter, recordingsFilter, null);
    }

    @Test
    void passesNullToAllDelegateMethodsWhenSupplierReturnsBlank() {
        AuthorizationAwareGraphQLClient client =
                new AuthorizationAwareGraphQLClient(delegate, () -> "   \n  ", SECURE_ENDPOINT);

        client.targetNodes(filter, false);
        client.environmentNodes(filter);
        client.targetNodes(filter, recordingsFilter);

        verify(delegate).targetNodes(filter, false, null);
        verify(delegate).environmentNodes(filter, null);
        verify(delegate).targetNodes(filter, recordingsFilter, null);
    }

    @Test
    void refusesToSendCredentialsOverRemoteCleartextEndpoint() {
        AuthorizationAwareGraphQLClient client =
                new AuthorizationAwareGraphQLClient(
                        delegate,
                        () -> "Bearer secret-token",
                        URI.create("http://cryostat.namespace.svc:8181/api/v4/graphql"));

        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> client.targetNodes(filter, false));

        assertTrue(
                e.getMessage().contains("Refusing to send Authorization credentials"),
                e.getMessage());
        assertFalse(e.getMessage().contains("secret-token"), e.getMessage());
        verifyNoInteractions(delegate);
    }

    @Test
    void queriesRemoteCleartextEndpointWithoutCredentials() {
        AuthorizationAwareGraphQLClient client =
                new AuthorizationAwareGraphQLClient(
                        delegate,
                        () -> null,
                        URI.create("http://cryostat.namespace.svc:8181/api/v4/graphql"));

        client.environmentNodes(filter);

        verify(delegate).environmentNodes(filter, null);
    }
}
