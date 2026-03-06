package org.acme.model;

import java.util.List;

public record ArchivedRecordingDirectory(
        String connectUrl, String jvmId, List<ArchivedRecordingDescriptor> recordings) {}
