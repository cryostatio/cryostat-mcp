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

import java.util.List;

/**
 * Strategy interface for aggregating results from multiple Cryostat instances. Each non-directed
 * tool implements this to define its aggregation behavior.
 *
 * @param <T> The type of result being aggregated
 */
@FunctionalInterface
public interface AggregationStrategy<T> {

    /**
     * Aggregate results from multiple Cryostat instances.
     *
     * @param results List of results from each instance (may contain nulls for failed instances)
     * @param instances List of instances that were queried
     * @return Aggregated result
     * @throws Exception if aggregation fails
     */
    T aggregate(List<T> results, List<CryostatInstance> instances) throws Exception;
}
