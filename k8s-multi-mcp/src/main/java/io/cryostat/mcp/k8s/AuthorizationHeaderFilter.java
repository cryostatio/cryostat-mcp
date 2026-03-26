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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * JAX-RS request/response filter that extracts the Authorization header from incoming requests and
 * stores it in the ClientCredentialsContext for forwarding to Cryostat instances. This filter
 * handles JAX-RS endpoints (if any exist). For Vert.x-handled MCP endpoints, see
 * VertxAuthorizationInterceptor.
 */
@Provider
@ApplicationScoped
public class AuthorizationHeaderFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger(AuthorizationHeaderFilter.class);

    @Inject ClientCredentialsContext credentialsContext;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String authHeader = requestContext.getHeaderString("Authorization");

        if (authHeader != null && !authHeader.isEmpty()) {
            LOG.debugf("Captured Authorization header from JAX-RS request");
            credentialsContext.setAuthorizationHeader(authHeader);
        } else {
            LOG.debugf("No Authorization header in JAX-RS request");
        }
    }

    @Override
    public void filter(
            ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        credentialsContext.clear();
        LOG.debugf("Cleared Authorization header after JAX-RS request");
    }
}
