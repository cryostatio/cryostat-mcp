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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Aggregation strategy for Prometheus-formatted metrics. Concatenates metrics from multiple
 * instances, sorts lines alphabetically, and removes duplicates to provide a unified view of
 * metrics across all instances.
 */
@ApplicationScoped
public class PrometheusMetricsAggregationStrategy implements AggregationStrategy<String> {

    /**
     * Aggregates Prometheus metrics from multiple Cryostat instances.
     *
     * <p>The aggregation process:
     *
     * <ol>
     *   <li>Filters out null results (from failed instances) and empty strings
     *   <li>Splits each result into individual lines
     *   <li>Filters out empty lines
     *   <li>Sorts lines alphabetically
     *   <li>Removes duplicate lines
     *   <li>Joins lines with newlines
     * </ol>
     *
     * @param results List of Prometheus metrics strings from each instance (may contain nulls)
     * @param instances List of instances that were queried
     * @return Aggregated and deduplicated Prometheus metrics, or empty string if no valid results
     */
    @Override
    public String aggregate(List<String> results, List<CryostatInstance> instances) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        // Filter out nulls (failed instances) and empty results
        List<String> validResults =
                results.stream().filter(r -> r != null && !r.isEmpty()).toList();

        if (validResults.isEmpty()) {
            return "";
        }

        // Concatenate, sort, and deduplicate metric lines
        return validResults.stream()
                .flatMap(metrics -> Arrays.stream(metrics.split("\n")))
                .filter(line -> !line.isEmpty())
                .sorted()
                .distinct()
                .collect(Collectors.joining("\n"));
    }
}
