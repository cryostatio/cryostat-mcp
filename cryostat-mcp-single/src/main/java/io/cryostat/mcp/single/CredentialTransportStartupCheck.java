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
package io.cryostat.mcp.single;

import java.net.URI;
import java.util.Optional;

import io.cryostat.mcp.CredentialTransportPolicy;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Fails startup when a static credential is configured alongside a cleartext Cryostat URL, rather
 * than letting the first REST or GraphQL request leak it. Both clients attach {@code
 * cryostat.auth.value} to every request, so the check belongs where the URLs are configured.
 *
 * @see CredentialTransportPolicy
 */
@ApplicationScoped
public class CredentialTransportStartupCheck {

    @ConfigProperty(name = "cryostat.auth.value")
    Optional<String> authorizationValue;

    @ConfigProperty(name = "quarkus.rest-client.cryostat.url")
    String restUrl;

    @ConfigProperty(name = "quarkus.smallrye-graphql-client.cryostat.url")
    String graphqlUrl;

    void onStart(@Observes StartupEvent event) {
        String credential =
                authorizationValue
                        .map(String::strip)
                        .filter(value -> !value.isEmpty())
                        .orElse(null);
        if (credential == null) {
            return;
        }
        CredentialTransportPolicy.requireSecureTransportUnchecked(URI.create(restUrl), credential);
        CredentialTransportPolicy.requireSecureTransportUnchecked(
                URI.create(graphqlUrl), credential);
    }
}
