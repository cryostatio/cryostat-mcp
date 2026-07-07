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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CryostatVersionTest {

    @Test
    void parseAcceptsLeadingVAndQualifier() {
        assertEquals(new CryostatVersion(4, 2, 1), CryostatVersion.parse("v4.2.1").orElseThrow());
        assertEquals(
                new CryostatVersion(4, 2, 1),
                CryostatVersion.parse("4.2.1-SNAPSHOT").orElseThrow());
    }

    @Test
    void parseDefaultsMissingComponentsToZero() {
        assertEquals(new CryostatVersion(4, 2, 0), CryostatVersion.parse("4.2").orElseThrow());
        assertEquals(new CryostatVersion(4, 0, 0), CryostatVersion.parse("4").orElseThrow());
    }

    @Test
    void parseRejectsInvalidValues() {
        assertTrue(CryostatVersion.parse(null).isEmpty());
        assertTrue(CryostatVersion.parse("").isEmpty());
        assertTrue(CryostatVersion.parse("latest").isEmpty());
    }

    @Test
    void isAtLeastComparesMajorMinorAndPatch() {
        CryostatVersion version = new CryostatVersion(4, 2, 1);

        assertTrue(version.isAtLeast(new CryostatVersion(4, 2, 0)));
        assertTrue(version.isAtLeast(new CryostatVersion(4, 2, 1)));
        assertFalse(version.isAtLeast(new CryostatVersion(4, 3, 0)));
    }
}
