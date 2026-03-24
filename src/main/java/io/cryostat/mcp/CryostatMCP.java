package io.cryostat.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cryostat.mcp.model.ArchivedRecordingDirectory;
import io.cryostat.mcp.model.DiscoveryNode;
import io.cryostat.mcp.model.DiscoveryNodeFilter;
import io.cryostat.mcp.model.EventTemplate;
import io.cryostat.mcp.model.Health;
import io.cryostat.mcp.model.RecordingDescriptor;
import io.cryostat.mcp.model.Target;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.rest.client.inject.RestClient;

public class CryostatMCP {

    @Inject @RestClient CryostatRESTClient rest;
    @Inject CryostatGraphQLClient graphql;
    @Inject ObjectMapper mapper;

    @Tool(description = "Get Cryostat server health and configuration")
    Health getHealth() {
        return rest.health();
    }

    @Tool(
            description =
                    """
                    Get the full discovery tree. This is a tree of Discovery Nodes, each of which may have zero or more child Discovery Nodes.
                    A Discovery Node is considered an "Environment Node" if it has child nodes and no embedded Target, which indicates that it
                    represents some intermediate object in the deployment environment - for example, a Kubernetes Namespace, Deployment,
                    ReplicaSet, or Pod. If a Discovery Node has no child nodes then it should have an embedded Target field, which indicates
                    that it represents a Target application.
                    The root of the tree is always the Universe node representing everything the Cryostat instance is aware of. The children of
                    the Universe are aways Realm nodes, representing each distinct Discovery Plugin (Kubernetes API, JDP, Docker/Podman,
                    Custom Targets, and each individual registered Cryostat Agent instance).
                    Cryostat Version: v2.1+
                    """)
    DiscoveryNode getDiscoveryTree(
            @ToolArg(
                            description =
                                    """
                                        Whether the Realms should be merged into a unified node. This is useful in a Kubernetes context, as it allows Cryostat Agent
                                        instances to appear not as distinct Realms of their own with independent Pod -> ReplicaSet -> Deployment -> Namespace lineages,
                                        but as a holistic merged view. This also allows the Cryostat Agent instances' subtrees to appear merged with any Kubernetes
                                        API EndpointSlices-discovered Targets.
                                    """,
                            defaultValue = "true")
                    boolean mergeRealms) {
        return rest.getDiscoveryTree(mergeRealms);
    }

    @Tool(
            description =
                    """
                    Get a list of all discovered Target applications. Each Target belongs to a Discovery Node. In a Kubernetes context
                    the Discovery Node will be a Pod or equivalent object. Searching for the Target associated with a particular Pod
                    can be done by querying this endpoint with the Pod's name as a filter input. If no filter inputs are provided,
                    the full list of all discovered Targets will be returned. Otherwise, if any filter inputs are provided, then only
                    Targets which match all of the given inputs will be returned.
                    Cryostat Version: v2.2+
                    """)
    List<io.cryostat.mcp.model.graphql.DiscoveryNode> listTargets(
            @ToolArg(
                            description =
                                    """
                                    List of Discovery Node IDs to match. Discovery Nodes matching any of the given IDs will be selected.
                                    """,
                            required = false)
                    List<Long> ids,
            @ToolArg(
                            description =
                                    """
                                    List of Target IDs to match. Targets matching any of the given IDs will be selected.
                                    """,
                            required = false)
                    List<Long> targetIds,
            @ToolArg(
                            description =
                                    """
                                    List of Discovery Node names to match. Discovery Nodes matching any of the given names will be selected.
                                    """,
                            required = false)
                    List<String> names,
            @ToolArg(
                            description =
                                    """
                                    List of Discovery Node label selectors to match. Discovery Nodes matching any of the given label selectors will be selected.
                                    Label selectors use the Kubernetes selector syntax: "my-label=foo", "my-label != bar", "env in (prod, stage)", "!present".
                                    """,
                            required = false)
                    List<String> labels) {
        DiscoveryNodeFilter filter = null;
        if (isPresent(ids) || isPresent(targetIds) || isPresent(names) || isPresent(labels)) {
            filter = DiscoveryNodeFilter.from(ids, targetIds, names, labels);
        }
        return graphql.targetNodes(filter);
    }

    static boolean isPresent(Collection<?> filter) {
        return filter != null && !filter.isEmpty();
    }

    @Tool(
            description =
                    """
                    Get information about a Target from historical database audit log. If the Target application may have been lost,
                    this tool may still be able to provide information about what the Target was if the Cryostat instance has audit
                    logging enabled.
                    Cryostat Version: v4.2+
                    """)
    Target getAuditTarget(
            @ToolArg(description = "The Target's JVM hash ID.", required = true) String jvmId) {
        return rest.auditTarget(jvmId);
    }

    @Tool(
            description =
                    """
                    Get a Target's DiscoveryNode lineage from historical database audit log. If the Cryostat instance has audit logging enabled,
                    this tool can return information about the Target and all of its DiscoveryNode lineage ancestors up to (but excluding)
                    the Universe node.
                    Cryostat Version: v4.2+
                    """)
    DiscoveryNode getAuditTargetLineage(
            @ToolArg(description = "The Target's JVM hash ID.", required = true) String jvmId) {
        return rest.auditTargetLineage(jvmId);
    }

    @Tool(
            description =
                    """
                    List the available JDK Flight Recorder Event Templates for a given Target.
                    Cryostat Version: v1.0+
                    """)
    List<EventTemplate> listTargetEventTemplates(
            @ToolArg(description = "The Target's ID.", required = true) long targetId) {
        return rest.targetEventTemplates(targetId);
    }

    @Tool(
            description =
                    """
                    Get a specific .jfc (XML) JDK Flight Recorder Event Template definition.
                    Cryostat Version: v1.0+
                    """)
    String getTargetEventTemplate(
            @ToolArg(description = "The Target's ID.", required = true) long targetId,
            @ToolArg(description = "The event template's type.", required = true)
                    String templateType,
            @ToolArg(description = "The event template's name.", required = true)
                    String templateName) {
        return rest.targetEventTemplate(targetId, templateType, templateName);
    }

    @Tool(
            description =
                    """
                    Get a list of active JDK Flight Recordings present in the Target JVM.
                    Cryostat Version: v1.0+
                    """)
    List<RecordingDescriptor> listTargetActiveRecordings(
            @ToolArg(description = "The Target's ID.", required = true) long targetId) {
        return rest.targetActiveRecordings(targetId);
    }

    @Tool(
            description =
                    """
                    Get a list of archived JDK Flight Recordings sourced from the Target JVM.
                    Cryostat Version: v4.0+
                    """)
    List<ArchivedRecordingDirectory> listTargetArchivedRecordings(
            @ToolArg(description = "The Target's JVM hash ID.", required = true) String jvmId) {
        return rest.targetArchivedRecordings(jvmId);
    }

    @Tool(
            description =
                    """
                    Start a new fixed-duration JDK Flight Recording on a Target JVM.
                    When the recording completes, Cryostat will automatically capture the data
                    and perform an automated analysis of its contents.
                    Cryostat Version: v1.0+
                    """)
    RecordingDescriptor startTargetRecording(
            @ToolArg(description = "The Target's ID.", required = true) long targetId,
            @ToolArg(
                            description = "The name of the recording. Must be unique per Target.",
                            required = true)
                    String recordingName,
            @ToolArg(
                            description =
                                    "The name of the Event Template to use for the recording.",
                            required = true)
                    String templateName,
            @ToolArg(
                            description =
                                    "The type of the Event Template to use for the recording.",
                            required = true)
                    String templateType,
            @ToolArg(description = "The duration of the recording in seconds.", required = true)
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

    @Tool(
            description =
                    """
                    Scrape the Prometheus-formatted automated analysis metrics. Any recently processed
                    automated analysis reports will appear here. These are raw scores. Use the JSON report
                    output for human-readable explanations and suggestions.
                    Scores of -1 indicate the JDK Flight Recorder event type required for this analysis was not configured.
                    Scores of 0 indicate that no problem was detected.
                    Scores of (0.0, 25.0) indicate that a low severity issue was detected.
                    Scores of [25.0, 75.0) indicate that a medium severity issue was detected.
                    Scores of [75.0, 100.0] indicate that a high severity issue was detected.
                    Cryostat Version: v4.1+
                    """)
    String scrapeMetrics(
            @ToolArg(
                            description =
                                    """
                                    Filter results by a minimum score. If a Target has any rule result greater than or equal
                                    to this value, then all rule results for that Target will be included. For example,
                                    setting this to 25.0 indicates that the response should not include any results for
                                    Targets which have no medium or high severity issues, and will include all results for
                                    all Targets which have at least one medium or high severity issue.
                                    """,
                            defaultValue = "-1.0")
                    double minTargetScore) {
        return rest.scrapeMetrics(minTargetScore);
    }

    @Tool(
            description =
                    """
                    Scrape the Prometheus-formatted automated analysis metrics for a specified Target.
                    The most recently processed automated analysis report metrics for this target will be returned,
                    if any are available. These are raw scores. Use the JSON report output for human-readable
                    explanations and suggestions.
                    Scores of -1 indicate the JDK Flight Recorder event type required for this analysis was not configured.
                    Scores of 0 indicate that no problem was detected.
                    Scores of (0.0, 25.0) indicate that a low severity issue was detected.
                    Scores of [25.0, 75.0) indicate that a medium severity issue was detected.
                    Scores of [75.0, 100.0] indicate that a high severity issue was detected.
                    Cryostat Version: v4.1+
                    """)
    String scrapeTargetMetrics(
            @ToolArg(description = "The Target's JVM hash ID.", required = true) String jvmId) {
        return rest.scrapeTargetMetrics(jvmId);
    }

    @Tool(
            description =
                    """
                    Get the JSON-formatted automated analysis report for a specified Target.
                    The most recently processed automated analysis report document for this target will be returned,
                    if any is available.
                    This is a comprehensive report document containing human-readable explanations, summaries,
                    and suggestions. For simple problem detection and incident reporting, use the Prometheus-format
                    metrics scraping tools.
                    Cryostat Version: v4.1+
                    """)
    Object getTargetReport(
            @ToolArg(description = "The Target's ID.", required = true) long targetId) {
        return rest.getTargetReport(targetId);
    }

    @Tool(
            description =
                    """
                    Load an archived Flight Recording file into a database and execute an Apache Calcite SQL
                    query against the contents of that file. The data is located in a schema named JFR, and
                    each JFR event type is mapped to a table sharing the event's name. Each event attribute
                    is mapped to a column of that table. The target's event templates can be inspected to
                    determine which events may be found in a given recording.
                    The JFR Calcite converter does NOT flatten nested fields like objectClass.name into separate
                    top-level columns. All complex objects remain as single columns containing serialized structures.
                    Fields like objectClass and eventThread are returned as formatted string representations of
                    their internal structure, not as separate queryable columns.
                    Queries cannot use "objectClass"."name", "objectClass.name", or "objectClass"['name'] syntax.
                    Cryostat Version: v4.2+
                    """)
    List<List<String>> executeQuery(
            @ToolArg(description = "The Target's JVM hash ID.", required = true) String jvmId,
            @ToolArg(
                            description = "The name of the archived recording file to query.",
                            required = true)
                    String filename,
            @ToolArg(description = "The SQL query to execute.", required = true) String query) {
        return rest.executeQuery(jvmId, filename, query);
    }

    @Tool(
            description =
                    """
                    Provides details about additional custom functions and structures available for SQL queries.
                    Cryostat Version: v4.2+
                    """)
    List<QueryExample> getQueryAdditionalFunctions() {
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

    @Tool(
            description =
                    """
                    Provides example SQL queries as reference.
                    Cryostat Version: v4.2+
                    """)
    List<QueryExample> getQueryExamples() {
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

    record QueryExample(String description, String query) {}
}
