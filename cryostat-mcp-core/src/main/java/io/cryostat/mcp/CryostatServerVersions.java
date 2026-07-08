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
package io.cryostat.mcp;

/**
 * Cryostat server version strings used as compile-time constants in MCP tool metadata.
 *
 * <p>This is not intended to be a complete Cryostat release catalog. Add only version boundaries
 * that are used by a tool minimum-version annotation or a feature gate.
 */
public final class CryostatServerVersions {

    public static final String ANY = "0.0";
    public static final String V4_0 = "4.0";
    public static final String V4_1 = "4.1";
    public static final String V4_2 = "4.2";
    public static final String V4_2_1 = "4.2.1";

    private CryostatServerVersions() {}
}
