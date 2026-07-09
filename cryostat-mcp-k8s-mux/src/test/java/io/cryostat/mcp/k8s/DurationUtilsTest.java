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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class DurationUtilsTest {

    // --- seconds range (< 60 s) ---

    @Test
    void testZeroMilliseconds() {
        assertEquals("0s", DurationUtils.humanize(Duration.ZERO));
    }

    @Test
    void testSubSecondRoundsToZeroSeconds() {
        assertEquals("0s", DurationUtils.humanize(Duration.ofMillis(499)));
    }

    @Test
    void testSubSecondRoundsUpToOneSecond() {
        assertEquals("1s", DurationUtils.humanize(Duration.ofMillis(500)));
    }

    @Test
    void testExactlyOneSecond() {
        assertEquals("1s", DurationUtils.humanize(Duration.ofSeconds(1)));
    }

    @Test
    void testAlmostThirtySecondsRoundsDown() {
        assertEquals("29s", DurationUtils.humanize(Duration.ofMillis(29499)));
    }

    @Test
    void testAlmostThirtySecondsRoundsUp() {
        // 29 999 ms → round(29.999) = 30
        assertEquals("30s", DurationUtils.humanize(Duration.ofMillis(29999)));
    }

    @Test
    void testFiftyNineSeconds() {
        assertEquals("59s", DurationUtils.humanize(Duration.ofSeconds(59)));
    }

    @Test
    void testFiftyNinePointFourSeconds() {
        assertEquals("59s", DurationUtils.humanize(Duration.ofMillis(59400)));
    }

    // --- minutes range (60 s <= duration < 3600 s) ---

    @Test
    void testExactlyOneMinute() {
        assertEquals("1m", DurationUtils.humanize(Duration.ofSeconds(60)));
    }

    @Test
    void testAlmostOneMinuteRoundsUpToOneMinute() {
        // 59 500 ms → totalSeconds = round(59.5) = 60 → totalMinutes = round(60/60) = 1
        assertEquals("1m", DurationUtils.humanize(Duration.ofMillis(59500)));
    }

    @Test
    void testFiveMinutesFromSeconds() {
        // 301 seconds → totalSeconds=301 → totalMinutes=round(301/60)=round(5.016)=5
        assertEquals("5m", DurationUtils.humanize(Duration.ofSeconds(301)));
    }

    @Test
    void testRoundingUpToNextMinute() {
        // 4 min 30 s = 270 s → round(270/60) = round(4.5) = 5
        assertEquals("5m", DurationUtils.humanize(Duration.ofSeconds(270)));
    }

    @Test
    void testFiftyNineMinutes() {
        assertEquals("59m", DurationUtils.humanize(Duration.ofSeconds(59 * 60)));
    }

    // --- hours range (>= 3600 s) ---

    @Test
    void testExactlyOneHour() {
        assertEquals("1h", DurationUtils.humanize(Duration.ofHours(1)));
    }

    @Test
    void testExactlyTwoHoursFromMinutes() {
        // 120 minutes → totalSeconds=7200 → totalMinutes=120 → totalHours=2
        assertEquals("2h", DurationUtils.humanize(Duration.ofMinutes(120)));
    }

    @Test
    void testOneHourThirtyMinutesRoundsToTwo() {
        // 90 minutes → totalSeconds=5400 → totalMinutes=90 → totalHours=round(90/60)=round(1.5)=2
        assertEquals("2h", DurationUtils.humanize(Duration.ofMinutes(90)));
    }

    @Test
    void testOneHourTwentyNineMinutesRoundsToOne() {
        // 89 minutes → totalSeconds=5340 → totalMinutes=89 → totalHours=round(89/60)=round(1.483)=1
        assertEquals("1h", DurationUtils.humanize(Duration.ofMinutes(89)));
    }

    @Test
    void testTwentyFourHours() {
        assertEquals("24h", DurationUtils.humanize(Duration.ofHours(24)));
    }
}
