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

import java.util.Date;

import org.junit.jupiter.api.Test;

class UnsatisfiableRangeExceptionTest {

    @Test
    void testMessageContainsJvmId() {
        String jvmId = "abc123";
        UnsatisfiableRangeException ex =
                new UnsatisfiableRangeException(jvmId, new Date(1000L), new Date(2000L));
        assertTrue(ex.getMessage().contains(jvmId));
    }

    @Test
    void testMessageContainsTimestamps() {
        Date from = new Date(1_000_000L);
        Date to = new Date(2_000_000L);
        UnsatisfiableRangeException ex = new UnsatisfiableRangeException("jvm-1", from, to);
        assertTrue(ex.getMessage().contains(from.toString()));
        assertTrue(ex.getMessage().contains(to.toString()));
    }
}
