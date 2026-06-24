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

import java.util.List;

import io.cryostat.CryostatPodNameMapper;
import io.cryostat.mcp.CryostatMCP;
import io.cryostat.mcp.model.EventTemplate;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class K8sOrientedTools {

    @Inject CryostatMCPInstanceManager instanceManager;
    @Inject CryostatPodNameMapper podNameMapper;

    @Tool(
            description =
                    "List the available JDK Flight Recorder Event Templates for a given"
                            + " application.")
    public List<EventTemplate> listTargetEventTemplatesByPod(
            @ToolArg(description = "The namespace of application.", required = true)
                    String namespace,
            @ToolArg(description = "The podName of the application", required = true)
                    String podName) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        var targetId = podNameMapper.getTargetId(namespace, podName);
        return mcp.listTargetEventTemplates(targetId);
    }
}
