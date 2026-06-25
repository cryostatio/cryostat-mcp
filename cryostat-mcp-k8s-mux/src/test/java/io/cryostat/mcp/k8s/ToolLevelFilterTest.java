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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import io.cryostat.mcp.k8s.ToolLevelFilter.ToolLevel;

import io.quarkiverse.mcp.server.FilterContext;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.ToolManager;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ToolLevelFilterTest {

    private ToolLevelFilter filter;

    @Mock private Logger logger;

    @Mock private FilterContext context;

    @BeforeEach
    void setUp() {
        filter = new ToolLevelFilter();
        filter.logger = logger;
    }

    private ToolManager.ToolInfo createToolInfo(String toolLevel) {
        ToolManager.ToolInfo toolInfo = mock(ToolManager.ToolInfo.class);
        Map<MetaKey, Object> metadata = new HashMap<>();
        if (toolLevel != null) {
            metadata.put(ToolLevelFilter.TOOL_LEVEL_KEY, toolLevel);
        }
        lenient().when(toolInfo.metadata()).thenReturn(metadata);
        return toolInfo;
    }

    @Test
    void testToolWithoutMetadata_PassesFilter() {
        filter.configLevel = ToolLevel.LOW;
        ToolManager.ToolInfo toolInfo = createToolInfo(null);

        boolean result = filter.test(toolInfo, context);

        assertTrue(result);
        verifyNoInteractions(logger);
    }

    @Test
    void testConfigLevelALL_AllowsLOWTool() {
        filter.configLevel = ToolLevel.ALL;
        ToolManager.ToolInfo toolInfo = createToolInfo("LOW");

        boolean result = filter.test(toolInfo, context);

        assertTrue(result);
    }

    @Test
    void testConfigLevelALL_AllowsHIGHTool() {
        filter.configLevel = ToolLevel.ALL;
        ToolManager.ToolInfo toolInfo = createToolInfo("HIGH");

        boolean result = filter.test(toolInfo, context);

        assertTrue(result);
    }

    @Test
    void testConfigLevelALL_AllowsALLTool() {
        filter.configLevel = ToolLevel.ALL;
        ToolManager.ToolInfo toolInfo = createToolInfo("ALL");

        boolean result = filter.test(toolInfo, context);

        assertTrue(result);
    }

    @Test
    void testConfigLevelALL_AllowsToolWithoutMetadata() {
        filter.configLevel = ToolLevel.ALL;
        ToolManager.ToolInfo toolInfo = createToolInfo(null);

        boolean result = filter.test(toolInfo, context);

        assertTrue(result);
    }

    @Test
    void testConfigLevelALL_AllowsInvalidToolLevel_WithWarning() {
        filter.configLevel = ToolLevel.ALL;
        ToolManager.ToolInfo toolInfo = createToolInfo("INVALID");

        boolean result = filter.test(toolInfo, context);

        assertTrue(result);
    }

    @Test
    void testConfigLevelLOW_AllowsLOWTool() {
        filter.configLevel = ToolLevel.LOW;
        ToolManager.ToolInfo toolInfo = createToolInfo("LOW");

        boolean result = filter.test(toolInfo, context);

        assertTrue(result);
    }

    @Test
    void testConfigLevelLOW_AllowsALLTool() {
        filter.configLevel = ToolLevel.LOW;
        ToolManager.ToolInfo toolInfo = createToolInfo("ANY");

        boolean result = filter.test(toolInfo, context);

        assertTrue(result);
    }

    @Test
    void testConfigLevelLOW_BlocksHIGHTool() {
        filter.configLevel = ToolLevel.LOW;
        ToolManager.ToolInfo toolInfo = createToolInfo("HIGH");

        boolean result = filter.test(toolInfo, context);

        assertFalse(result);
    }

    @Test
    void testConfigLevelLOW_AllowsToolWithoutMetadata() {
        filter.configLevel = ToolLevel.LOW;
        ToolManager.ToolInfo toolInfo = createToolInfo(null);

        boolean result = filter.test(toolInfo, context);

        assertTrue(result);
    }

    @Test
    void testConfigLevelHIGH_AllowsHIGHTool() {
        filter.configLevel = ToolLevel.HIGH;
        ToolManager.ToolInfo toolInfo = createToolInfo("HIGH");

        boolean result = filter.test(toolInfo, context);

        assertTrue(result);
    }

    @Test
    void testConfigLevelHIGH_AllowsALLTool() {
        filter.configLevel = ToolLevel.HIGH;
        ToolManager.ToolInfo toolInfo = createToolInfo("ANY");

        boolean result = filter.test(toolInfo, context);

        assertTrue(result);
    }

    @Test
    void testConfigLevelHIGH_BlocksLOWTool() {
        filter.configLevel = ToolLevel.HIGH;
        ToolManager.ToolInfo toolInfo = createToolInfo("LOW");

        boolean result = filter.test(toolInfo, context);

        assertFalse(result);
    }

    @Test
    void testConfigLevelHIGH_AllowsToolWithoutMetadata() {
        filter.configLevel = ToolLevel.HIGH;
        ToolManager.ToolInfo toolInfo = createToolInfo(null);

        boolean result = filter.test(toolInfo, context);

        assertTrue(result);
    }

    @Test
    void testInvalidToolLevel_PassesWithWarning_ConfigLevelLOW() {
        filter.configLevel = ToolLevel.LOW;
        ToolManager.ToolInfo toolInfo = createToolInfo("INVALID_LEVEL");

        boolean result = filter.test(toolInfo, context);

        assertTrue(result);
        verify(logger).warn(any(IllegalArgumentException.class));
    }

    @Test
    void testInvalidToolLevel_PassesWithWarning_ConfigLevelHIGH() {
        filter.configLevel = ToolLevel.HIGH;
        ToolManager.ToolInfo toolInfo = createToolInfo("MEDIUM");

        boolean result = filter.test(toolInfo, context);

        assertTrue(result);
        verify(logger).warn(any(IllegalArgumentException.class));
    }

    @Test
    void testInvalidToolLevel_PassesWithWarning_ConfigLevelALL() {
        filter.configLevel = ToolLevel.ALL;
        ToolManager.ToolInfo toolInfo = createToolInfo("UNKNOWN");

        boolean result = filter.test(toolInfo, context);

        assertTrue(result);
    }

    @Test
    void testEmptyStringToolLevel_PassesWithWarning() {
        filter.configLevel = ToolLevel.LOW;
        ToolManager.ToolInfo toolInfo = createToolInfo("");

        boolean result = filter.test(toolInfo, context);

        assertTrue(result);
        verify(logger).warn(any(IllegalArgumentException.class));
    }

    @Test
    void testNullStringToolLevel_PassesWithoutWarning() {
        filter.configLevel = ToolLevel.LOW;
        ToolManager.ToolInfo toolInfo = createToolInfo(null);

        boolean result = filter.test(toolInfo, context);

        assertTrue(result);
        verifyNoInteractions(logger);
    }

    @Test
    void testCaseSensitiveToolLevel_LowercaseFails() {
        filter.configLevel = ToolLevel.LOW;
        ToolManager.ToolInfo toolInfo = createToolInfo("low");

        boolean result = filter.test(toolInfo, context);

        assertTrue(result);
        verify(logger).warn(any(IllegalArgumentException.class));
    }

    @Test
    void testCaseSensitiveToolLevel_MixedCaseFails() {
        filter.configLevel = ToolLevel.HIGH;
        ToolManager.ToolInfo toolInfo = createToolInfo("High");

        boolean result = filter.test(toolInfo, context);

        assertTrue(result);
        verify(logger).warn(any(IllegalArgumentException.class));
    }

    @Test
    void testMultipleCallsWithSameConfig_ConsistentResults() {
        filter.configLevel = ToolLevel.LOW;
        ToolManager.ToolInfo lowTool = createToolInfo("LOW");
        ToolManager.ToolInfo highTool = createToolInfo("HIGH");

        assertTrue(filter.test(lowTool, context));
        assertFalse(filter.test(highTool, context));
        assertTrue(filter.test(lowTool, context));
        assertFalse(filter.test(highTool, context));
    }

    @Test
    void testConfigLevelALL_AlwaysReturnsTrue() {
        filter.configLevel = ToolLevel.ALL;

        assertTrue(filter.test(createToolInfo("LOW"), context));
        assertTrue(filter.test(createToolInfo("HIGH"), context));
        assertTrue(filter.test(createToolInfo("ALL"), context));
        assertTrue(filter.test(createToolInfo(null), context));
        assertTrue(filter.test(createToolInfo("INVALID"), context));
    }
}
