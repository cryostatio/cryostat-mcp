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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import io.cryostat.mcp.model.Health;

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
class CryostatToolVersionFilterTest {

    private CryostatToolVersionFilter filter;

    @Mock private CryostatRESTClientWithAuth rest;
    @Mock private Logger logger;
    @Mock private FilterContext context;

    @BeforeEach
    void setUp() {
        filter = new CryostatToolVersionFilter();
        filter.rest = rest;
        filter.logger = logger;
    }

    @Test
    void allowsToolWithoutVersionMetadata() {
        ToolManager.ToolInfo tool = createToolInfo("tool", null);

        assertTrue(filter.test(tool, context));

        verifyNoInteractions(rest);
    }

    @Test
    void allowsToolWhenServerVersionMeetsMinimum() {
        when(rest.health()).thenReturn(health("4.2.0"));
        ToolManager.ToolInfo tool = createToolInfo("tool", "4.1");

        assertTrue(filter.test(tool, context));
    }

    @Test
    void blocksToolWhenServerVersionIsBelowMinimum() {
        when(rest.health()).thenReturn(health("4.0.3"));
        ToolManager.ToolInfo tool = createToolInfo("tool", "4.1");

        assertFalse(filter.test(tool, context));
    }

    @Test
    void allowsToolWhenMinimumVersionMetadataIsInvalid() {
        ToolManager.ToolInfo tool = createToolInfo("tool", "latest");

        assertTrue(filter.test(tool, context));

        verifyNoInteractions(rest);
        verify(logger).warnf(anyString(), eq("latest"), eq("tool"));
    }

    @Test
    void allowsToolWhenServerVersionCannotBeDetermined() {
        when(rest.health()).thenThrow(new RuntimeException("unavailable"));
        ToolManager.ToolInfo tool = createToolInfo("tool", "4.1");

        assertTrue(filter.test(tool, context));

        verify(logger).warn(anyString(), any(RuntimeException.class));
        verify(logger).warnf(anyString(), eq("tool"));
    }

    private static ToolManager.ToolInfo createToolInfo(String name, String minimumVersion) {
        ToolManager.ToolInfo tool = mock(ToolManager.ToolInfo.class);
        Map<MetaKey, Object> metadata = new HashMap<>();
        if (minimumVersion != null) {
            metadata.put(CryostatToolVersionFilter.MIN_CRYOSTAT_VERSION_KEY, minimumVersion);
        }
        lenient().when(tool.name()).thenReturn(name);
        when(tool.metadata()).thenReturn(metadata);
        return tool;
    }

    private static Health health(String version) {
        return new Health(version, false, false, false, false, false, false, null);
    }
}
