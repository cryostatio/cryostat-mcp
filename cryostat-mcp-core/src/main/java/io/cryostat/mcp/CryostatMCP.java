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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import io.cryostat.mcp.model.ActiveRecordingsFilter;
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
import jakarta.ws.rs.WebApplicationException;

public class CryostatMCP {

    static final int ARCHIVE_POLL_ATTEMPTS = 6;
    static final long ARCHIVE_INITIAL_DELAY_MS = 3_000L;
    static final long ARCHIVE_RETRY_DELAY_MS = 5_000L;
    static final Duration REPORT_NOTIFICATION_TIMEOUT = Duration.ofSeconds(30);

    private final CryostatRESTClient rest;
    private final CryostatGraphQLClient graphql;
    private final ObjectMapper mapper;
    private volatile Optional<CryostatVersion> serverVersion;
    private final Supplier<String> authorizationHeader;
    private final URI baseUri;
    private final HttpClient httpClient;

    public CryostatMCP(
            URI baseUri,
            String authorizationHeader,
            CryostatRESTClient rest,
            CryostatGraphQLClient graphql,
            ObjectMapper mapper) {
        this(baseUri, () -> authorizationHeader, rest, graphql, mapper);
    }

    public static CryostatMCP withAuthorizationHeaderSupplier(
            URI baseUri,
            Supplier<String> authorizationHeader,
            CryostatRESTClient rest,
            CryostatGraphQLClient graphql,
            ObjectMapper mapper) {
        return new CryostatMCP(baseUri, authorizationHeader, rest, graphql, mapper);
    }

    private CryostatMCP(
            URI baseUri,
            Supplier<String> authorizationHeader,
            CryostatRESTClient rest,
            CryostatGraphQLClient graphql,
            ObjectMapper mapper) {
        this.httpClient = HttpClient.newHttpClient();
        this.baseUri = baseUri;
        this.authorizationHeader = authorizationHeader;
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
            List<String> aliases,
            List<String> labels,
            List<String> annotations,
            Boolean useAuditLog) {
        DiscoveryNodeFilter filter = null;
        if (isPresent(ids)
                || isPresent(targetIds)
                || isPresent(names)
                || isPresent(aliases)
                || isPresent(labels)
                || isPresent(annotations)) {
            filter =
                    DiscoveryNodeFilter.builder()
                            .ids(ids)
                            .targetIds(targetIds)
                            .names(names)
                            .aliases(aliases)
                            .labels(labels)
                            .annotations(annotations)
                            .build();
        }
        return graphql.targetNodes(filter, useAuditLog);
    }

    public List<io.cryostat.mcp.model.graphql.DiscoveryNode> listTargetsForPodName(
            String podName, Boolean useAuditLog) {
        if (supports(CryostatFeature.TARGET_ALIAS_FILTER)) {
            return listTargets(null, null, null, List.of(podName), null, null, useAuditLog);
        }
        return listTargets(null, null, null, null, null, List.of("HOST==" + podName), useAuditLog);
    }

    public List<io.cryostat.mcp.model.graphql.DiscoveryNode> listEnvironmentNodes(
            List<String> nodeTypes, List<String> names) {
        DiscoveryNodeFilter filter = null;
        if (isPresent(nodeTypes) || isPresent(names)) {
            filter = DiscoveryNodeFilter.builder().names(names).nodeTypes(nodeTypes).build();
        }
        return graphql.environmentNodes(filter);
    }

    public boolean supports(CryostatFeature feature) {
        return getServerVersion()
                .map(version -> version.isAtLeast(feature.minimumVersion()))
                .orElse(false);
    }

    public Optional<CryostatVersion> getServerVersion() {
        Optional<CryostatVersion> current = serverVersion;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (serverVersion == null) {
                try {
                    serverVersion =
                            Optional.ofNullable(rest.health())
                                    .flatMap(
                                            health ->
                                                    CryostatVersion.parse(
                                                            health.cryostatVersion()));
                } catch (RuntimeException e) {
                    serverVersion = Optional.empty();
                }
            }
            return serverVersion;
        }
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
        ActiveRecordingsFilter recordingFilter = new ActiveRecordingsFilter(recordingName);
        return graphql.targetNodes(nodeFilter, recordingFilter).stream()
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
        return emptyIfNull(rest.scrapeMetrics(minTargetScore));
    }

    public String scrapeTargetMetrics(String jvmId) {
        try {
            return emptyIfNull(rest.scrapeTargetMetrics(jvmId));
        } catch (WebApplicationException e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
                return "";
            }
            throw e;
        }
    }

    public Object getTargetReport(long targetId) {
        return rest.getTargetReport(targetId);
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private String normalizeHeader(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    public InputStream downloadArchivedRecording(String jvmId, String filename) throws IOException {
        String downloadUrl =
                listTargetArchivedRecordings(jvmId).stream()
                        .flatMap(dir -> dir.recordings().stream())
                        .filter(r -> r.name().equals(filename))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Archived recording not found: " + filename))
                        .downloadUrl();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(resolveUri(downloadUrl)).GET();
        String authorizationHeader = this.authorizationHeader.get();
        if (authorizationHeader != null && !authorizationHeader.isEmpty()) {
            requestBuilder.header("Authorization", authorizationHeader);
        }
        try {
            return httpClient
                    .send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream())
                    .body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading recording: " + filename, e);
        }
    }

    public String getArchivedReport(String jvmId, String filename) throws IOException {
        String reportUrl =
                listTargetArchivedRecordings(jvmId).stream()
                        .flatMap(dir -> dir.recordings().stream())
                        .filter(r -> r.name().equals(filename))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Archived recording not found: " + filename))
                        .reportUrl();
        URI resolvedReportUri = resolveUri(reportUrl);
        ReportNotificationListener listener = new ReportNotificationListener();
        WebSocket webSocket = connectNotifications(listener);
        try {
            HttpResponse<String> response = sendStringGet(resolvedReportUri);
            if (response.statusCode() == 200) {
                return response.body();
            }
            if (response.statusCode() != 202) {
                throw new IOException(
                        "Unexpected response while fetching report for "
                                + filename
                                + ": HTTP "
                                + response.statusCode());
            }
            String jobId = response.body().trim();
            boolean success = listener.awaitJob(jobId, REPORT_NOTIFICATION_TIMEOUT);
            if (!success) {
                throw new IOException("Report generation failed for: " + filename);
            }
            HttpResponse<String> completed = sendStringGet(resolvedReportUri);
            if (completed.statusCode() != 200) {
                throw new IOException(
                        "Unexpected response while fetching completed report for "
                                + filename
                                + ": HTTP "
                                + completed.statusCode());
            }
            return completed.body();
        } finally {
            closeWebSocket(webSocket);
        }
    }

    public ArchivedRecordingDescriptor uploadArchivedRecording(
            String jvmId, String filename, File recording, Map<String, String> labels)
            throws IOException {
        String labelsJson = mapper.writeValueAsString(labels);
        rest.uploadArchivedRecording(jvmId, recording, labelsJson);
        return listTargetArchivedRecordings(jvmId).stream()
                .flatMap(dir -> dir.recordings().stream())
                .filter(r -> r.name().equals(filename))
                .findFirst()
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "Uploaded recording not found: " + filename));
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

    URI resolveUri(String pathOrUri) {
        URI uri = URI.create(pathOrUri);
        if (uri.isAbsolute()) {
            return uri;
        }
        return baseUri.resolve(uri);
    }

    HttpResponse<String> sendStringGet(URI uri) throws IOException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri).GET();
        String authorizationHeader = normalizeHeader(this.authorizationHeader.get());
        if (authorizationHeader != null) {
            requestBuilder.header("Authorization", authorizationHeader);
        }
        try {
            return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching URI: " + uri, e);
        }
    }

    private WebSocket connectNotifications(ReportNotificationListener listener) throws IOException {
        URI notificationsUri = notificationsUri();
        var builder = httpClient.newWebSocketBuilder();
        String authorizationHeader = normalizeHeader(this.authorizationHeader.get());
        if (authorizationHeader != null) {
            builder.header("Authorization", authorizationHeader);
        }
        try {
            return builder.buildAsync(notificationsUri, listener).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while connecting notifications WebSocket", e);
        } catch (ExecutionException e) {
            throw new IOException("Failed to connect notifications WebSocket", e.getCause());
        }
    }

    private URI notificationsUri() {
        String scheme = baseUri.getScheme();
        String wsScheme = "https".equalsIgnoreCase(scheme) ? "wss" : "ws";
        return URI.create(wsScheme + "://" + baseUri.getAuthority() + "/api/notifications");
    }

    private void closeWebSocket(WebSocket webSocket) throws IOException {
        try {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while closing notifications WebSocket", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("Failed to close notifications WebSocket", e);
        }
    }

    private final class ReportNotificationListener implements WebSocket.Listener {
        private final CompletableFuture<Boolean> result = new CompletableFuture<>();
        private final StringBuilder text = new StringBuilder();
        private volatile String awaitedJobId;

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                handleMessage(text.toString());
                text.setLength(0);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            result.completeExceptionally(error);
        }

        boolean awaitJob(String jobId, Duration timeout) throws IOException {
            awaitedJobId = jobId;
            try {
                return result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while awaiting report notification", e);
            } catch (ExecutionException e) {
                throw new IOException("Failed while awaiting report notification", e.getCause());
            } catch (TimeoutException e) {
                throw new IOException(
                        "Timed out awaiting report notification for job: " + jobId, e);
            }
        }

        private void handleMessage(String json) {
            try {
                Map<?, ?> payload = mapper.readValue(json, Map.class);
                Object metaObj = payload.get("meta");
                Object messageObj = payload.get("message");
                if (!(metaObj instanceof Map<?, ?> meta)
                        || !(messageObj instanceof Map<?, ?> message)) {
                    return;
                }
                Object categoryObj = meta.get("category");
                Object jobIdObj = message.get("jobId");
                if (!(categoryObj instanceof String category)
                        || !(jobIdObj instanceof String jobId)
                        || !jobId.equals(awaitedJobId)) {
                    return;
                }
                if ("ReportSuccess".equals(category)) {
                    result.complete(true);
                } else if ("ReportFailure".equals(category)) {
                    result.complete(false);
                }
            } catch (JsonProcessingException e) {
                // ignore unrelated or malformed notifications
            }
        }
    }

    public record QueryExample(String description, String query) {}
}
