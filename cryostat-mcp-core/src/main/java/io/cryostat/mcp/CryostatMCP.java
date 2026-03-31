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
import java.util.List;
import java.util.Map;

import io.cryostat.mcp.model.ArchivedRecordingDirectory;
import io.cryostat.mcp.model.DiscoveryNode;
import io.cryostat.mcp.model.DiscoveryNodeFilter;
import io.cryostat.mcp.model.EventTemplate;
import io.cryostat.mcp.model.Health;
import io.cryostat.mcp.model.RecordingDescriptor;
import io.cryostat.mcp.model.Target;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CryostatMCP {

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
            Boolean useAuditLog) {
        DiscoveryNodeFilter filter = null;
        if (isPresent(ids) || isPresent(targetIds) || isPresent(names) || isPresent(labels)) {
            filter = DiscoveryNodeFilter.from(ids, targetIds, names, labels);
        }
        return graphql.targetNodes(filter, useAuditLog);
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
