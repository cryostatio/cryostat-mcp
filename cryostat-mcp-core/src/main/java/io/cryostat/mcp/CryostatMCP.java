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
package io.cryostat.mcp;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import io.cryostat.mcp.model.ActiveRecordingFilter;
import io.cryostat.mcp.model.ArchivedRecordingDescriptor;
import io.cryostat.mcp.model.ArchivedRecordingDirectory;
import io.cryostat.mcp.model.DiscoveryNode;
import io.cryostat.mcp.model.DiscoveryNodeFilter;
import io.cryostat.mcp.model.EventTemplate;
import io.cryostat.mcp.model.Health;
import io.cryostat.mcp.model.RecordingDescriptor;
import io.cryostat.mcp.model.Target;
import io.cryostat.mcp.model.graphql.StoppedRecording;
import io.cryostat.mcp.model.graphql.TargetNodeForStop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CryostatMCP {

    static final int ARCHIVE_POLL_ATTEMPTS = 6;
    static final long ARCHIVE_INITIAL_DELAY_MS = 3_000L;
    static final long ARCHIVE_RETRY_DELAY_MS = 5_000L;

    private final CryostatRESTClient rest;
    private final CryostatGraphQLClient graphql;
    private final ObjectMapper mapper;

    public CryostatMCP(
            CryostatRESTClient rest, CryostatGraphQLClient graphql, ObjectMapper mapper) {
        this.rest = rest;
        this.graphql = graphql;
        this.mapper = mapper;
    }

    public Health getHealth() {
        return rest.health();
    }

    public DiscoveryNode getDiscoveryTree(boolean mergeRealms) {
        return rest.getDiscoveryTree(mergeRealms);
    }

    public List<io.cryostat.mcp.model.graphql.DiscoveryNode> listTargets(
            List<Long> ids,
            List<Long> targetIds,
            List<String> names,
            List<String> labels,
            List<String> annotations,
            Boolean useAuditLog) {
        DiscoveryNodeFilter filter = null;
        if (isPresent(ids)
                || isPresent(targetIds)
                || isPresent(names)
                || isPresent(labels)
                || isPresent(annotations)) {
            filter =
                    DiscoveryNodeFilter.builder()
                            .ids(ids)
                            .targetIds(targetIds)
                            .names(names)
                            .labels(labels)
                            .annotations(annotations)
                            .build();
        }
        return graphql.targetNodes(filter, useAuditLog);
    }

    public List<io.cryostat.mcp.model.graphql.DiscoveryNode> listEnvironmentNodes(
            List<String> nodeTypes, List<String> names) {
        DiscoveryNodeFilter filter = null;
        if (isPresent(nodeTypes) || isPresent(names)) {
            filter = DiscoveryNodeFilter.builder().names(names).nodeTypes(nodeTypes).build();
        }
        return graphql.environmentNodes(filter);
    }

    static boolean isPresent(Collection<?> filter) {
        return filter != null && !filter.isEmpty();
    }

    public Target getAuditTarget(String jvmId) {
        return rest.auditTarget(jvmId);
    }

    public DiscoveryNode getAuditTargetLineage(String jvmId) {
        return rest.auditTargetLineage(jvmId);
    }

    public List<EventTemplate> listTargetEventTemplates(long targetId) {
        return rest.targetEventTemplates(targetId);
    }

    public String getTargetEventTemplate(long targetId, String templateType, String templateName) {
        return rest.targetEventTemplate(targetId, templateType, templateName);
    }

    public List<RecordingDescriptor> listTargetActiveRecordings(long targetId) {
        return rest.targetActiveRecordings(targetId);
    }

    public List<ArchivedRecordingDirectory> listTargetArchivedRecordings(String jvmId) {
        return rest.targetArchivedRecordings(jvmId);
    }

    public ArchivedRecordingDescriptor archiveTargetRecording(long targetId, String jvmId) {
        RecordingDescriptor snapshot = rest.createSnapshot(targetId);
        rest.patchRecording(targetId, snapshot.remoteId(), "save");
        String snapshotName = snapshot.name();
        sleep(ARCHIVE_INITIAL_DELAY_MS);
        try {
            for (int attempt = 0; attempt < ARCHIVE_POLL_ATTEMPTS; attempt++) {
                if (attempt > 0) {
                    sleep(ARCHIVE_RETRY_DELAY_MS);
                }
                Optional<ArchivedRecordingDescriptor> result =
                        findArchivedSnapshot(jvmId, snapshotName);
                if (result.isPresent()) {
                    return result.get();
                }
            }
            throw new NoSuchElementException("Archived recording not found: " + snapshotName);
        } finally {
            rest.deleteRecording(targetId, snapshot.remoteId());
        }
    }

    private Optional<ArchivedRecordingDescriptor> findArchivedSnapshot(
            String jvmId, String snapshotName) {
        return rest.targetArchivedRecordings(jvmId).stream()
                .flatMap(dir -> dir.recordings().stream())
                .filter(
                        r -> {
                            for (String part : r.name().split("_")) {
                                if (part.equals(snapshotName)) {
                                    return true;
                                }
                            }
                            return false;
                        })
                .max(Comparator.comparingLong(ArchivedRecordingDescriptor::archivedTime));
    }

    void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public RecordingDescriptor startTargetRecording(
            long targetId,
            String recordingName,
            String templateName,
            String templateType,
            long duration)
            throws JsonProcessingException {
        return rest.startRecording(
                targetId,
                recordingName,
                String.format("template=%s,type=%s", templateName, templateType),
                duration,
                true,
                mapper.writeValueAsString(Map.of("labels", Map.of("autoanalyze", "true"))),
                true);
    }

    public StoppedRecording stopTargetRecording(long targetId, String recordingName) {
        DiscoveryNodeFilter nodeFilter =
                DiscoveryNodeFilter.builder().targetIds(List.of(targetId)).build();
        ActiveRecordingFilter recordingFilter = new ActiveRecordingFilter(recordingName);
        return graphql.stopActiveRecording(nodeFilter, recordingFilter).stream()
                .map(TargetNodeForStop::target)
                .filter(t -> t != null)
                .map(t -> t.activeRecordings())
                .filter(ar -> ar != null && ar.data() != null)
                .flatMap(ar -> ar.data().stream())
                .map(node -> node.doStop())
                .filter(r -> r != null)
                .findFirst()
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "Active recording not found: " + recordingName));
    }

    public String scrapeMetrics(double minTargetScore) {
        return rest.scrapeMetrics(minTargetScore);
    }

    public String scrapeTargetMetrics(String jvmId) {
        return rest.scrapeTargetMetrics(jvmId);
    }

    public Object getTargetReport(long targetId) {
        return rest.getTargetReport(targetId);
    }

    public List<List<String>> executeQuery(String jvmId, String filename, String query) {
        return rest.executeQuery(jvmId, filename, query);
    }

    public List<List<String>> listArchivedRecordingEventTypes(String jvmId, String filename) {
        return rest.executeQuery(jvmId, filename, JfrAnalyticsQueries.listEventTypesQuery());
    }

    public List<List<String>> listArchivedRecordingEventFields(
            String jvmId, String filename, String eventType) {
        return rest.executeQuery(
                jvmId, filename, JfrAnalyticsQueries.listEventFieldsQuery(eventType));
    }

    public List<List<String>> listArchivedRecordingEvents(
            String jvmId, String filename, String eventType, List<String> columns, int limit) {
        return rest.executeQuery(
                jvmId, filename, JfrAnalyticsQueries.listEventsQuery(eventType, columns, limit));
    }

    public List<List<String>> listArchivedRecordingEvents(
            String jvmId, String filename, String eventType, int limit) {
        return listArchivedRecordingEvents(jvmId, filename, eventType, null, limit);
    }

    public List<QueryExample> getQueryAdditionalFunctions() {
        return List.of(
                new QueryExample(
                        "Obtains the fully-qualified class name from the given"
                                + " jdk.jfr.consumer.RecordedClass",
                        "VARCHAR CLASS_NAME(RecordedClass)"),
                new QueryExample(
                        "Truncates the stacktrace of the given jdk.jfr.consumer.RecordedStackTrace"
                                + " to the given depth",
                        "VARCHAR TRUNCATE_STACKTRACE(RecordedStackTrace, INT):"),
                new QueryExample(
                        "Returns true if the given jdk.jfr.consumer.RecordedStackTrace contains a"
                                + " frame matching the given regular expression, false otherwise",
                        "BOOL HAS_MATCHING_FRAME(RecordedStackTrace, VARCHAR):"),
                new QueryExample(
                        "The following additional struct type is available",
                        """
                            RecordedThread {
                                osName
                                osThreadId
                                javaName
                                javaThreadId
                                group
                            }
                        """));
    }

    public List<QueryExample> getQueryExamples() {
        return List.of(
                new QueryExample(
                        "List the available JFR event types (tables) in a recording", "tables"),
                new QueryExample(
                        "List the JFR event type fields (columns) on a given table",
                        "columns jdk.ObjectAllocationSample"),
                new QueryExample(
                        "Count the number of object allocation sample events",
                        """
                        SELECT COUNT(*) FROM jfr."jdk.ObjectAllocationSample"
                        """),
                new QueryExample(
                        "Retrieve the ten top allocating stacktraces",
                        """
                        SELECT TRUNCATE_STACKTRACE("stackTrace", 40), SUM("weight")
                                FROM jfr."jdk.ObjectAllocationSample"
                                GROUP BY TRUNCATE_STACKTRACE("stackTrace", 40)
                                ORDER BY SUM("weight") DESC
                                LIMIT 10
                        """),
                new QueryExample(
                        "Retrieve the top 20 classes by allocation count",
                        """
                        SELECT CLASS_NAME("objectClass") AS "class_name",
                                COUNT(*) AS "allocation_count"
                                FROM jfr."jdk.ObjectAllocationSample"
                                GROUP BY CLASS_NAME("objectClass")
                                ORDER BY COUNT(*) DESC
                                LIMIT 20
                        """),
                new QueryExample(
                        """
                        Retrieve several columns of information about the first class loaded by the JVM
                        """,
                        """
                        SELECT "startTime", "loadedClass", "initiatingClassLoader", "definingClassLoader"
                                FROM jfr."jdk.ClassLoad"
                                ORDER by "startTime"
                                LIMIT 1
                        """),
                new QueryExample(
                        "Retrieve the name of the first class loaded by the JVM",
                        """
                        SELECT CLASS_NAME("loadedClass") as className
                                FROM jfr."jdk.ClassLoad"
                                ORDER by "startTime"
                                LIMIT 1
                        """),
                new QueryExample(
                        "Get information about threads which are no longer running",
                        """
                        SELECT ts."parentThread"."javaName", ts."thread"."javaName", ts."thread"."javaThreadId", te."thread"."javaName", te."thread"."javaThreadId"
                                FROM jfr."jdk.ThreadStart" ts
                                LEFT JOIN jfr."jdk.ThreadEnd" te ON ts."thread"."javaThreadId" = te."thread"."javaThreadId"
                                ORDER BY ts."thread"."javaThreadId"
                        """));
    }

    public record QueryExample(String description, String query) {}
}
