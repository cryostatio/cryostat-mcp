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

import io.quarkiverse.mcp.server.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * System tools that operate on the multi-MCP system itself. These tools do not call any underlying
 * Cryostat MCP instances.
 */
@ApplicationScoped
public class SystemTools {

    @Inject CryostatInstanceDiscovery discovery;

    @Tool(
            description =
                    "List all discovered Cryostat instances (services) in the Kubernetes cluster."
                            + " Returns information about each instance including name, namespace,"
                            + " application URL, and target namespaces being monitored.")
    public List<CryostatInstance> listCryostatInstances() {
        return discovery.getAllInstances().stream().toList();
    }
}
