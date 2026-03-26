package io.cryostat.mcp.model.graphql;

import java.util.Map;

public record DiscoveryNode(
        long id, String name, String nodeType, Map<String, String> labels, Target target) {}
