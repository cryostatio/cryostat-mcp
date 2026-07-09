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

import java.time.Duration;

/** Utility methods for working with time durations. */
public final class DurationUtils {

    private DurationUtils() {}

    /**
     * Converts a {@link Duration} to a human-readable string rounded to the most appropriate unit,
     * using compact unit suffixes with no spaces (e.g. {@code "30s"}, {@code "5m"}, {@code "2h"}).
     *
     * <ul>
     *   <li>Durations under 60 seconds are expressed in seconds (rounded from milliseconds).
     *   <li>Durations under 1 hour are expressed in minutes (rounded from seconds).
     *   <li>All other durations are expressed in hours (rounded from minutes).
     * </ul>
     */
    public static String humanize(Duration duration) {
        long totalSeconds = Math.round(duration.toMillis() / 1000.0);
        long totalMinutes = Math.round(totalSeconds / 60.0);
        long totalHours = Math.round(totalMinutes / 60.0);

        if (totalSeconds < 60) {
            return totalSeconds + "s";
        } else if (totalSeconds < 3600) {
            return totalMinutes + "m";
        } else {
            return totalHours + "h";
        }
    }
}
