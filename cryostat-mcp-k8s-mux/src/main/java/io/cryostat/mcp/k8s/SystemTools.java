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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * System tools that operate on the multi-MCP system itself. These tools do not call any underlying
 * Cryostat MCP instances.
 */
@ApplicationScoped
public class SystemTools {

    private static final Logger LOG = Logger.getLogger(SystemTools.class);

    @Inject ToolManager toolManager;
    @Inject CryostatInstanceDiscovery discovery;
    @Inject ObjectMapper objectMapper;

    void onStart(@Observes StartupEvent event) {
        LOG.info("Registering system MCP tools");
        registerSystemTools();
    }

    /**
     * Registers system tools that operate on the multi-MCP system itself. These tools do not call
     * any underlying Cryostat MCP instances.
     */
    private void registerSystemTools() {
        // Register listCryostatInstances tool
        var toolBuilder =
                toolManager
                        .newTool("listCryostatInstances")
                        .setDescription(
                                "List all discovered Cryostat instances (services) in the"
                                        + " Kubernetes cluster. Returns information about each"
                                        + " instance including name, namespace, application URL,"
                                        + " and target namespaces being monitored.");

        toolBuilder.setHandler(
                toolArgs -> {
                    try {
                        List<CryostatInstance> instances =
                                discovery.getAllInstances().stream().toList();
                        String jsonResult = objectMapper.writeValueAsString(instances);
                        return ToolResponse.success(jsonResult);
                    } catch (Exception e) {
                        LOG.errorf(e, "Failed to list Cryostat instances");
                        String errorMsg = e.getMessage();
                        if (errorMsg == null || errorMsg.isEmpty()) {
                            errorMsg = e.getClass().getSimpleName() + ": " + e.toString();
                        }
                        return ToolResponse.error("Failed to list instances: " + errorMsg);
                    }
                });

        toolBuilder.register();

        LOG.info("Registered 1 system tool");
    }
}