/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.mcp.k8s;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.cryostat.mcp.CryostatMCP;
import io.cryostat.mcp.model.ArchivedRecordingDescriptor;
import io.cryostat.mcp.model.KeyValue;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Utility bean that returns or synthesizes an archived recording covering a requested time range.
 */
@ApplicationScoped
public class ArchivedRecordingSynthesizer {

    @Inject CryostatMCPInstanceManager instanceManager;

    @Inject Logger log;

    Path tempDir;

    void onStart(@Observes StartupEvent evt) throws IOException {
        tempDir = Files.createTempDirectory("cryostat-mcp-synthetic-");
    }

    /**
     * Return a single {@link ArchivedRecordingDescriptor} whose window covers (or at least
     * intersects) the requested time range {@code [fromTimestamp, toTimestamp]}. If exactly one
     * recording intersects the range it is returned as-is. If multiple recordings intersect the
     * range they are downloaded, concatenated in {@code startTime} order, re-uploaded, and the
     * resulting descriptor is returned. If no recordings intersect the range an {@link
     * UnsatisfiableRangeException} is thrown.
     */
    public ArchivedRecordingDescriptor synthesize(
            String namespace, String podName, String jvmId, Date fromTimestamp, Date toTimestamp)
            throws UnsatisfiableRangeException, IOException {
        CryostatMCP mcp = instanceManager.createInstance(namespace);

        List<ArchivedRecordingDescriptor> candidates =
                mcp.listTargetArchivedRecordings(jvmId).stream()
                        .flatMap(dir -> dir.recordings().stream())
                        .filter(r -> intersects(r, fromTimestamp, toTimestamp))
                        .sorted(Comparator.comparingLong(r -> startTimeOf(r)))
                        .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            throw new UnsatisfiableRangeException(jvmId, fromTimestamp, toTimestamp);
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        return synthesizeMultiple(mcp, podName, jvmId, candidates);
    }

    private ArchivedRecordingDescriptor synthesizeMultiple(
            CryostatMCP mcp,
            String podName,
            String jvmId,
            List<ArchivedRecordingDescriptor> candidates)
            throws IOException {
        long minStart = candidates.stream().mapToLong(this::startTimeOf).min().getAsLong();
        long maxEnd =
                candidates.stream()
                        .mapToLong(r -> startTimeOf(r) + durationOf(r))
                        .max()
                        .getAsLong();
        long syntheticDuration = maxEnd - minStart;

        String isoStart =
                Instant.ofEpochMilli(minStart).toString().replace(':', '-').replace('.', '-');
        String humanDuration = DurationUtils.humanize(Duration.ofMillis(syntheticDuration));
        String syntheticFilename = String.format("%s_%s_%s.jfr", podName, isoStart, humanDuration);

        Path tempFile = tempDir.resolve(syntheticFilename);
        try {
            try (OutputStream out = Files.newOutputStream(tempFile)) {
                for (ArchivedRecordingDescriptor candidate : candidates) {
                    try (InputStream in = mcp.downloadArchivedRecording(jvmId, candidate.name())) {
                        in.transferTo(out);
                    }
                }
            }

            Map<String, String> labels =
                    Map.of(
                            "jvmId",
                            jvmId,
                            "startTime",
                            String.valueOf(minStart),
                            "duration",
                            String.valueOf(syntheticDuration),
                            "synthetic",
                            "true",
                            "autoanalyze",
                            "true");

            return mcp.uploadArchivedRecording(jvmId, syntheticFilename, tempFile.toFile(), labels);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private boolean intersects(ArchivedRecordingDescriptor r, Date from, Date to) {
        long start = startTimeOf(r);
        long end = start + durationOf(r);
        return start < to.getTime() && end > from.getTime();
    }

    private long startTimeOf(ArchivedRecordingDescriptor r) {
        return extractLabelLong(r, "startTime");
    }

    private long durationOf(ArchivedRecordingDescriptor r) {
        return extractLabelLong(r, "duration");
    }

    private long extractLabelLong(ArchivedRecordingDescriptor r, String key) {
        String value = extractLabelValue(r, key);
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(value);
    }

    private String extractLabelValue(ArchivedRecordingDescriptor r, String key) {
        if (r.metadata() == null || r.metadata().labels() == null) {
            return null;
        }
        return r.metadata().labels().stream()
                .filter(kv -> key.equals(kv.key()))
                .map(KeyValue::value)
                .findFirst()
                .orElse(null);
    }
}
