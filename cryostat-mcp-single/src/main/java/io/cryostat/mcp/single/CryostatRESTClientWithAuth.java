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

import io.cryostat.mcp.CryostatRESTClient;

import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/** Extension of CryostatRESTClient that adds authorization header injection from configuration. */
@RegisterRestClient(configKey = "cryostat", baseUri = "http://localhost:8181")
@ClientHeaderParam(name = "Authorization", value = "${cryostat.auth.value}")
public interface CryostatRESTClientWithAuth extends CryostatRESTClient {}
