package io.cryostat.mcp.model;

import java.util.List;

public record DiscoveryNodeFilter(
        List<Long> ids, List<Long> targetIds, List<String> names, List<String> labels) {
    public static DiscoveryNodeFilter from(
            List<Long> ids, List<Long> targetIds, List<String> names, List<String> labels) {
        return new DiscoveryNodeFilter(ids, targetIds, names, labels);
    }
}
