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

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record CryostatVersion(int major, int minor, int patch)
        implements Comparable<CryostatVersion> {

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^v?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?.*$", Pattern.CASE_INSENSITIVE);

    public static Optional<CryostatVersion> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = VERSION_PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(
                new CryostatVersion(
                        Integer.parseInt(matcher.group(1)),
                        parseOptionalComponent(matcher.group(2)),
                        parseOptionalComponent(matcher.group(3))));
    }

    private static int parseOptionalComponent(String raw) {
        return raw == null ? 0 : Integer.parseInt(raw);
    }

    @Override
    public int compareTo(CryostatVersion other) {
        int majorComparison = Integer.compare(major, other.major);
        if (majorComparison != 0) {
            return majorComparison;
        }
        int minorComparison = Integer.compare(minor, other.minor);
        if (minorComparison != 0) {
            return minorComparison;
        }
        return Integer.compare(patch, other.patch);
    }

    public boolean isAtLeast(CryostatVersion minimumVersion) {
        return compareTo(minimumVersion) >= 0;
    }

    @Override
    public String toString() {
        return "%d.%d.%d".formatted(major, minor, patch);
    }
}
