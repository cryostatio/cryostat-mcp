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

public enum CryostatFeature {
    SYSTEM(CryostatServerVersions.ANY),
    HEALTH(CryostatServerVersions.ANY),
    EVENT_TEMPLATES(CryostatServerVersions.V4_0),
    ACTIVE_RECORDINGS(CryostatServerVersions.V4_0),
    RECORDING_CONTROL(CryostatServerVersions.V4_0),
    DISCOVERY_TREE(CryostatServerVersions.V4_0),
    TARGET_DISCOVERY(CryostatServerVersions.V4_0),
    ARCHIVED_RECORDINGS(CryostatServerVersions.V4_0),
    AUTOMATED_ANALYSIS(CryostatServerVersions.V4_1),
    AUDIT(CryostatServerVersions.V4_2),
    RECORDING_ANALYTICS(CryostatServerVersions.V4_2),
    TARGET_ALIAS_FILTER(CryostatServerVersions.V4_2_1);

    private final String minimumVersionString;
    private final CryostatVersion minimumVersion;

    CryostatFeature(String minimumVersionString) {
        this.minimumVersionString = minimumVersionString;
        this.minimumVersion =
                CryostatVersion.parse(minimumVersionString)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Invalid Cryostat version: "
                                                        + minimumVersionString));
    }

    public String minimumVersionString() {
        return minimumVersionString;
    }

    public CryostatVersion minimumVersion() {
        return minimumVersion;
    }
}
