package io.cryostat.mcp.model;

public record ArchivedRecordingDescriptor(
        String jvmId,
        String name,
        String downloadUrl,
        String reportUrl,
        Metadata metadata,
        long size,
        long archivedTime) {}
