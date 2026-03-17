package io.cryostat.mcp;

import io.cryostat.mcp.model.DiscoveryNodeFilter;
import io.cryostat.mcp.model.graphql.DiscoveryNode;
import io.smallrye.graphql.client.typesafe.api.GraphQLClientApi;
import java.util.List;

@GraphQLClientApi(configKey = "cryostat")
public interface CryostatGraphQLClient {
    List<DiscoveryNode> targetNodes(DiscoveryNodeFilter filter);
}
