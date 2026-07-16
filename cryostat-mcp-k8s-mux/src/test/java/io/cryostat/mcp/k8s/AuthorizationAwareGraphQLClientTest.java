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

import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicReference;

import io.cryostat.mcp.model.ActiveRecordingsFilter;
import io.cryostat.mcp.model.DiscoveryNodeFilter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizationAwareGraphQLClientTest {

    @Mock AuthorizationAwareGraphQLClient.Delegate delegate;
    @Mock DiscoveryNodeFilter filter;
    @Mock ActiveRecordingsFilter recordingsFilter;

    @Test
    void suppliesCurrentAuthorizationHeaderForEachInvocation() {
        AtomicReference<String> authorizationHeader =
                new AtomicReference<>("Bearer per-invocation-token");
        AuthorizationAwareGraphQLClient client =
                new AuthorizationAwareGraphQLClient(delegate, authorizationHeader::get);

        client.targetNodes(filter, false);

        verify(delegate).targetNodes(filter, false, "Bearer per-invocation-token");

        authorizationHeader.set(null);
        client.environmentNodes(filter);

        verify(delegate).environmentNodes(filter, null);

        authorizationHeader.set("Bearer static-token");
        client.targetNodes(filter, recordingsFilter);

        verify(delegate).targetNodes(filter, recordingsFilter, "Bearer static-token");
    }
}
