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

import java.util.Objects;
import java.util.function.Supplier;

import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/** Obtains the per-invocation Cryostat authorization header from an HTTP MCP request. */
@ApplicationScoped
public class CryostatAuthorization {

    public static final String PASSTHROUGH_HEADER = "Cryostat-Authorization";

    private final ThreadLocal<String> propagatedAuthorization = new ThreadLocal<>();

    @Inject Instance<RoutingContext> routingContexts;

    /**
     * Returns the optional authorization header supplied by the MCP client for the current HTTP
     * request. This deliberately does not inspect {@code Authorization}; that header remains
     * available for a future MCP-server authentication mechanism.
     */
    public String getPassthroughAuthorizationHeader() {
        String propagated = propagatedAuthorization.get();
        if (propagated != null) {
            return propagated;
        }

        if (!routingContexts.isResolvable()) {
            return null;
        }

        try {
            return normalize(routingContexts.get().request().getHeader(PASSTHROUGH_HEADER));
        } catch (ContextNotActiveException ignored) {
            // The manager is also used outside an HTTP tool invocation, where static credentials
            // (if configured) remain available.
            return null;
        }
    }

    /**
     * Runs an action with a captured passthrough credential. This is used when work leaves the HTTP
     * request context, such as non-directed tool calls running on virtual threads.
     */
    <T> T withPassthroughAuthorizationHeader(String authorizationHeader, Supplier<T> action) {
        Objects.requireNonNull(action);
        String previous = propagatedAuthorization.get();
        String normalized = normalize(authorizationHeader);
        if (normalized == null) {
            propagatedAuthorization.remove();
        } else {
            propagatedAuthorization.set(normalized);
        }
        try {
            return action.get();
        } finally {
            if (previous == null) {
                propagatedAuthorization.remove();
            } else {
                propagatedAuthorization.set(previous);
            }
        }
    }

    private String normalize(String authorizationHeader) {
        return authorizationHeader == null || authorizationHeader.isBlank()
                ? null
                : authorizationHeader;
    }
}
