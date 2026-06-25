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

public final class JfrAnalyticsQueries {

    public static final String LIST_EVENT_TYPES_QUERY = "tables";

    private JfrAnalyticsQueries() {}

    public static String listEventFieldsQuery(String eventType) {
        return "columns " + requireEventType(eventType);
    }

    public static String listEventsQuery(String eventType, int limit) {
        return String.format(
                "SELECT * FROM jfr.%s LIMIT %d",
                quoteSqlIdentifier(requireEventType(eventType)), requirePositiveLimit(limit));
    }

    private static String requireEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        return eventType.strip();
    }

    private static int requirePositiveLimit(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        return limit;
    }

    private static String quoteSqlIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
