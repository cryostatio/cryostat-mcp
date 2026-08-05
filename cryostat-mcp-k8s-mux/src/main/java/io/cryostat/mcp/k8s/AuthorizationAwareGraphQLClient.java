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
import java.util.Objects;
import java.util.function.Supplier;

import io.cryostat.mcp.model.ActiveRecordingsFilter;
import io.cryostat.mcp.model.DiscoveryNodeFilter;
import io.cryostat.mcp.model.graphql.DiscoveryNode;
import io.cryostat.mcp.model.graphql.TargetNodeForStop;

import io.smallrye.graphql.client.typesafe.api.Header;
import io.smallrye.graphql.client.typesafe.api.NestedParameter;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

/** Supplies the current invocation's credential to a reusable GraphQL client. */
class AuthorizationAwareGraphQLClient implements CryostatGraphQLClientImpl {

    interface Delegate {

        List<DiscoveryNode> targetNodes(
                @Name("filter") DiscoveryNodeFilter filter,
                @Name("useAuditLog") Boolean useAuditLog,
                @Header(name = "Authorization") String authorizationHeader);

        List<DiscoveryNode> environmentNodes(
                @Name("filter") DiscoveryNodeFilter filter,
                @Header(name = "Authorization") String authorizationHeader);

        @Query("targetNodes")
        List<TargetNodeForStop> targetNodes(
                @Name("filter") DiscoveryNodeFilter filter,
                @NestedParameter("target.activeRecordings") @Name("filter")
                        ActiveRecordingsFilter recordingsFilter,
                @Header(name = "Authorization") String authorizationHeader);
    }

    private final Delegate delegate;
    private final Supplier<String> authorizationHeader;

    AuthorizationAwareGraphQLClient(Delegate delegate, Supplier<String> authorizationHeader) {
        this.delegate = Objects.requireNonNull(delegate);
        this.authorizationHeader = Objects.requireNonNull(authorizationHeader);
    }

    @Override
    public List<DiscoveryNode> targetNodes(DiscoveryNodeFilter filter, Boolean useAuditLog) {
        return delegate.targetNodes(filter, useAuditLog, normalizeHeader());
    }

    @Override
    public List<DiscoveryNode> environmentNodes(DiscoveryNodeFilter filter) {
        return delegate.environmentNodes(filter, normalizeHeader());
    }

    @Override
    public List<TargetNodeForStop> targetNodes(
            DiscoveryNodeFilter filter, ActiveRecordingsFilter recordingsFilter) {
        return delegate.targetNodes(filter, recordingsFilter, normalizeHeader());
    }

    private String normalizeHeader() {
        return StringUtils.stripToNull(authorizationHeader.get());
    }
}
