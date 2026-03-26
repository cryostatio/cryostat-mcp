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

import jakarta.enterprise.context.RequestScoped;

/**
 * Request-scoped context for storing client credentials (Authorization header). This allows
 * credentials to be forwarded from the incoming request to sub-MCP instances.
 */
@RequestScoped
public class ClientCredentialsContext {

    private String authorizationHeader;

    /**
     * Get the Authorization header value from the current request.
     *
     * @return the Authorization header value, or null if not set
     */
    public String getAuthorizationHeader() {
        return authorizationHeader;
    }

    /**
     * Set the Authorization header value for the current request.
     *
     * @param authorizationHeader the Authorization header value
     */
    public void setAuthorizationHeader(String authorizationHeader) {
        this.authorizationHeader = authorizationHeader;
    }

    /**
     * Check if credentials are present.
     *
     * @return true if Authorization header is set, false otherwise
     */
    public boolean hasCredentials() {
        return authorizationHeader != null && !authorizationHeader.isEmpty();
    }
}
