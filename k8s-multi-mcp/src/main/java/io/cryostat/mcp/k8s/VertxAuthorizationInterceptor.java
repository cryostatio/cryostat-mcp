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

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Vert.x-based interceptor that captures the Authorization header from incoming HTTP requests to
 * MCP endpoints and stores it in the Vert.x context for forwarding to Cryostat instances.
 */
@ApplicationScoped
public class VertxAuthorizationInterceptor {

    private static final Logger LOG = Logger.getLogger(VertxAuthorizationInterceptor.class);
    private static final String AUTH_HEADER_KEY = "cryostat.auth.header";

    @Inject ClientCredentialsContext credentialsContext;

    void registerFilter(@Observes Filters filters) {
        filters.register(
                new Handler<RoutingContext>() {
                    @Override
                    public void handle(RoutingContext rc) {
                        String path = rc.request().path();

                        if (path.startsWith("/mcp/")) {
                            String authHeader = rc.request().getHeader("Authorization");

                            if (authHeader != null && !authHeader.isEmpty()) {
                                LOG.debugf(
                                        "Captured Authorization header from MCP request to %s",
                                        path);
                                rc.put(AUTH_HEADER_KEY, authHeader);
                                credentialsContext.setAuthorizationHeader(authHeader);
                            } else {
                                LOG.debugf("No Authorization header in MCP request to %s", path);
                            }

                            rc.addEndHandler(
                                    ar -> {
                                        credentialsContext.clear();
                                        LOG.debugf(
                                                "Cleared Authorization header after request to %s",
                                                path);
                                    });
                        }

                        rc.next();
                    }
                },
                10);
    }

    public static String getAuthorizationHeader(RoutingContext rc) {
        return rc.get(AUTH_HEADER_KEY);
    }
}
