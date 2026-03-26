package io.cryostat.mcp.model.graphql;

import java.util.Map;

public record Annotations(Map<String, String> platform, Map<String, String> cryostat) {}
