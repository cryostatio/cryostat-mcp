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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Request filter that extracts the Authorization header from incoming requests and stores it in the
 * ClientCredentialsContext for forwarding to Cryostat instances.
 */
@Provider
@ApplicationScoped
public class AuthorizationHeaderFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(AuthorizationHeaderFilter.class);

    @Inject ClientCredentialsContext credentialsContext;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String authHeader = requestContext.getHeaderString("Authorization");

        if (authHeader != null && !authHeader.isEmpty()) {
            LOG.debugf("Captured Authorization header from incoming request");
            credentialsContext.setAuthorizationHeader(authHeader);
        } else {
            LOG.debugf("No Authorization header in incoming request");
        }
    }
}
