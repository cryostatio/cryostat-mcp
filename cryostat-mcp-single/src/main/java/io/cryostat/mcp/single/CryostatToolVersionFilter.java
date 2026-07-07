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

import java.util.Optional;

import io.cryostat.mcp.CryostatToolMetadata;
import io.cryostat.mcp.CryostatVersion;

import io.quarkiverse.mcp.server.FilterContext;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.ToolFilter;
import io.quarkiverse.mcp.server.ToolManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

@Singleton
public class CryostatToolVersionFilter implements ToolFilter {

    public static final MetaKey MIN_CRYOSTAT_VERSION_KEY =
            new MetaKey(
                    CryostatToolMetadata.META_PREFIX,
                    CryostatToolMetadata.MIN_CRYOSTAT_VERSION_META_NAME);

    @RestClient CryostatRESTClientWithAuth rest;

    @Inject Logger logger;

    private volatile Optional<CryostatVersion> serverVersion;

    @Override
    public boolean test(ToolManager.ToolInfo tool, FilterContext context) {
        Object rawMinimumVersion = tool.metadata().get(MIN_CRYOSTAT_VERSION_KEY);
        if (rawMinimumVersion == null) {
            return true;
        }

        Optional<CryostatVersion> minimumVersion =
                CryostatVersion.parse(rawMinimumVersion.toString());
        if (minimumVersion.isEmpty()) {
            logger.warnf(
                    "Invalid minimum Cryostat version metadata '%s' for tool '%s'",
                    rawMinimumVersion, tool.name());
            return true;
        }

        Optional<CryostatVersion> version = getServerVersion();
        if (version.isEmpty()) {
            logger.warnf(
                    "Unable to determine Cryostat server version; allowing tool '%s'", tool.name());
            return true;
        }

        return version.get().isAtLeast(minimumVersion.get());
    }

    Optional<CryostatVersion> getServerVersion() {
        Optional<CryostatVersion> current = serverVersion;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (serverVersion == null) {
                try {
                    serverVersion =
                            Optional.ofNullable(rest.health())
                                    .flatMap(
                                            health ->
                                                    CryostatVersion.parse(
                                                            health.cryostatVersion()));
                } catch (RuntimeException e) {
                    logger.warn("Unable to read Cryostat server health", e);
                    serverVersion = Optional.empty();
                }
            }
            return serverVersion;
        }
    }
}
