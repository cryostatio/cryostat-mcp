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

import java.util.List;

public final class JfrAnalyticsQueries {

    private static final String LIST_EVENT_TYPES_QUERY = "tables";

    private JfrAnalyticsQueries() {}

    public static String listEventTypesQuery() {
        return LIST_EVENT_TYPES_QUERY;
    }

    public static String listEventFieldsQuery(String eventType) {
        return "columns " + requireEventType(eventType);
    }

    public static String listEventsQuery(String eventType, List<String> columns, int limit) {
        return String.format(
                "SELECT %s FROM jfr.%s LIMIT %d",
                selectColumns(columns),
                quoteSqlIdentifier(requireEventType(eventType)),
                requirePositiveLimit(limit));
    }

    public static String listEventsQuery(String eventType, int limit) {
        return listEventsQuery(eventType, null, limit);
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

    private static String selectColumns(List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            return "*";
        }
        return String.join(
                ", ",
                columns.stream()
                        .map(JfrAnalyticsQueries::requireColumn)
                        .map(JfrAnalyticsQueries::quoteSqlIdentifier)
                        .toList());
    }

    private static String requireColumn(String column) {
        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException("columns must not contain blank values");
        }
        return column.strip();
    }

    private static String quoteSqlIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
