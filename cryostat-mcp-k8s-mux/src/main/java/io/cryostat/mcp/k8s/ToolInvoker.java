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

import java.util.Map;

import io.cryostat.mcp.CryostatMCP;

/**
 * Functional interface for invoking a tool on a single Cryostat instance. Used by non-directed
 * tools to define how to call the underlying operation on each individual instance before
 * aggregation.
 *
 * @param <T> The type of result returned by the tool invocation
 */
@FunctionalInterface
public interface ToolInvoker<T> {

    /**
     * Invoke the tool on a single Cryostat instance.
     *
     * @param mcp The CryostatMCP client for the instance
     * @param args The tool arguments provided by the user
     * @return The result from this instance
     * @throws Exception if the invocation fails
     */
    T invoke(CryostatMCP mcp, Map<String, Object> args) throws Exception;
}
