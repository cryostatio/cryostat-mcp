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

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;

/** Adds the credential selected for the current MCP invocation to a downstream REST request. */
class CryostatAuthorizationFilter implements ClientRequestFilter {

    private final Supplier<String> authorizationHeader;

    CryostatAuthorizationFilter(Supplier<String> authorizationHeader) {
        this.authorizationHeader = Objects.requireNonNull(authorizationHeader);
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        String header = authorizationHeader.get();
        if (header != null && !header.isBlank()) {
            requestContext.getHeaders().putSingle(HttpHeaders.AUTHORIZATION, header);
        }
    }
}
