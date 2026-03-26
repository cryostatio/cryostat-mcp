package io.cryostat.mcp.model;

public record RecordingDescriptor(
        long id,
        long remoteId,
        String state,
        long duration,
        long startTime,
        boolean archiveOnStop,
        boolean continuous,
        boolean toDisk,
        long maxSize,
        long maxAge,
        String name,
        String downloadUrl,
        String reportUrl,
        Metadata metadata) {}
