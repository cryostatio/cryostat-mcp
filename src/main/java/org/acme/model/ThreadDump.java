package org.acme.model;

public record ThreadDump(
        String jvmId,
        String downloadUrl,
        String threadDumpId,
        long lastModified,
        long size,
        Metadata metadata) {}
