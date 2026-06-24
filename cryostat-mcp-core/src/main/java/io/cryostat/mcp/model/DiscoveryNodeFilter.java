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
package io.cryostat.mcp.model;

import java.util.List;

public record DiscoveryNodeFilter(
        List<Long> ids,
        List<Long> targetIds,
        List<String> names,
        List<String> labels,
        List<String> nodeTypes) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<Long> ids;
        private List<Long> targetIds;
        private List<String> names;
        private List<String> labels;
        private List<String> nodeTypes;

        private Builder() {}

        public Builder ids(List<Long> ids) {
            this.ids = ids;
            return this;
        }

        public Builder targetIds(List<Long> targetIds) {
            this.targetIds = targetIds;
            return this;
        }

        public Builder names(List<String> names) {
            this.names = names;
            return this;
        }

        public Builder labels(List<String> labels) {
            this.labels = labels;
            return this;
        }

        public Builder nodeTypes(List<String> nodeTypes) {
            this.nodeTypes = nodeTypes;
            return this;
        }

        public DiscoveryNodeFilter build() {
            return new DiscoveryNodeFilter(ids, targetIds, names, labels, nodeTypes);
        }
    }
}
