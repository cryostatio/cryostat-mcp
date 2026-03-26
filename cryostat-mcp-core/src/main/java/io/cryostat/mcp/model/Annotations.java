package io.cryostat.mcp.model;

import java.util.List;

public record Annotations(List<KeyValue> platform, List<KeyValue> cryostat) {}
