package io.cryostat.mcp.model;

import java.util.List;

public record DiscoveryNode(
        long id,
        String name,
        String nodeType,
        List<KeyValue> labels,
        List<DiscoveryNode> children,
        Target target) {}
