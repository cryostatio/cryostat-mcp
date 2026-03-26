package io.cryostat.mcp.model;

import java.util.List;

public record Target(
        long id,
        String connectUrl,
        String alias,
        String jvmId,
        List<KeyValue> labels,
        Annotations annotations) {}
