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

import io.quarkiverse.mcp.server.FilterContext;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.ToolFilter;
import io.quarkiverse.mcp.server.ToolManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@Singleton
public class ToolLevelFilter implements ToolFilter {

    public static final String TOOL_LEVEL_META_PREFIX = "cryostat.io/";
    public static final String TOOL_LEVEL_META_NAME = "tool-level";
    public static final MetaKey TOOL_LEVEL_KEY =
            new MetaKey(TOOL_LEVEL_META_PREFIX, TOOL_LEVEL_META_NAME);

    @ConfigProperty(name = "cryostat.mcp.tool-level")
    ToolLevel configLevel;

    @Inject Logger logger;

    @Override
    public boolean test(ToolManager.ToolInfo tool, FilterContext context) {
        if (configLevel == ToolLevel.BOTH) {
            return true;
        }
        Object o = tool.metadata().get(TOOL_LEVEL_KEY);
        if (o == null) {
            return true;
        }
        try {
            String rawToolLevel = o.toString();
            ToolLevel toolLevel = ToolLevel.valueOf(rawToolLevel);
            return toolLevel == configLevel;
        } catch (IllegalArgumentException e) {
            logger.warn(e);
            return true;
        }
    }

    public static enum ToolLevel {
        HIGH,
        LOW,
        BOTH,
        ;
    }
}
