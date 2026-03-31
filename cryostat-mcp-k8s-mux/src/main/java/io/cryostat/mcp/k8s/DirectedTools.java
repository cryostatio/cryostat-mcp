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

import java.util.List;

import io.cryostat.mcp.CryostatMCP;
import io.cryostat.mcp.model.ArchivedRecordingDirectory;
import io.cryostat.mcp.model.DiscoveryNode;
import io.cryostat.mcp.model.EventTemplate;
import io.cryostat.mcp.model.Health;
import io.cryostat.mcp.model.RecordingDescriptor;
import io.cryostat.mcp.model.Target;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Directed tools that require a namespace parameter to route requests to a specific Cryostat
 * instance. These are explicit @Tool-annotated methods that wrap core library methods.
 */
@ApplicationScoped
public class DirectedTools {

    @Inject CryostatMCPInstanceManager instanceManager;

    @Tool(
            description =
                    "Get Cryostat server health and configuration. Namespace parameter is required"
                            + " to identify the Cryostat instance managing the target.")
    public Health getHealth(
            @ToolArg(description = "The namespace of the Cryostat instance to query (required).")
                    String namespace) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        return mcp.getHealth();
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
                     Namespace parameter is required to identify the Cryostat instance managing the target.
                    """)
    public DiscoveryNode getDiscoveryTree(
            @ToolArg(description = "The namespace of the Cryostat instance to query (required).")
                    String namespace,
            @ToolArg(
                            description =
                                    """
                                        Whether the Realms should be merged into a unified node. This is useful in a Kubernetes context, as it allows Cryostat Agent
                                        instances to appear not as distinct Realms of their own with independent Pod -> ReplicaSet -> Deployment -> Namespace lineages,
                                        but as a holistic merged view. This also allows the Cryostat Agent instances' subtrees to appear merged with any Kubernetes
                                        API EndpointSlices-discovered Targets.
                                    """,
                            required = true)
                    boolean mergeRealms) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        return mcp.getDiscoveryTree(mergeRealms);
    }

    @Tool(
            description =
                    "Get a list of all discovered Target applications. Each Target belongs to a"
                        + " Discovery Node. In a Kubernetes context the Discovery Node will be a"
                        + " Pod or equivalent object. Searching for the Target associated with a"
                        + " particular Pod can be done by querying this endpoint with the Pod's"
                        + " name as a filter input. If no filter inputs are provided, the full list"
                        + " of all discovered Targets will be returned. Otherwise, if any filter"
                        + " inputs are provided, then only Targets which match all of the given"
                        + " inputs will be returned. Namespace parameter is required to identify"
                        + " the Cryostat instance managing the target.")
    public List<io.cryostat.mcp.model.graphql.DiscoveryNode> listTargets(
            @ToolArg(description = "The namespace of the Cryostat instance to query (required).")
                    String namespace,
            @ToolArg(
                            description =
                                    "List of Discovery Node IDs to match. Discovery Nodes matching"
                                            + " any of the given IDs will be selected.")
                    List<Long> ids,
            @ToolArg(
                            description =
                                    "List of Target IDs to match. Targets matching any of the given"
                                            + " IDs will be selected.")
                    List<Long> targetIds,
            @ToolArg(
                            description =
                                    "List of Discovery Node names to match. Discovery Nodes"
                                            + " matching any of the given names will be selected.")
                    List<String> names,
            @ToolArg(
                            description =
                                    "List of Discovery Node label selectors to match. Discovery"
                                        + " Nodes matching any of the given label selectors will be"
                                        + " selected. Label selectors use the Kubernetes selector"
                                        + " syntax: \"my-label=foo\", \"my-label != bar\", \"env in"
                                        + " (prod, stage)\", \"!present\".")
                    List<String> labels,
            @ToolArg(
                            description =
                                    "Query historical targets from audit log. This is more"
                                        + " expensive and should only be used when historical data"
                                        + " is needed.")
                    Boolean useAuditLog) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        return mcp.listTargets(ids, targetIds, names, labels, useAuditLog);
    }

    @Tool(
            description =
                    "Get information about a Target from historical database audit log. If the"
                        + " Target application may have been lost, this tool may still be able to"
                        + " provide information about what the Target was if the Cryostat instance"
                        + " has audit logging enabled. Namespace parameter is required to identify"
                        + " the Cryostat instance managing the target.")
    public Target getAuditTarget(
            @ToolArg(description = "The namespace of the Cryostat instance to query (required).")
                    String namespace,
            @ToolArg(description = "The Target's JVM hash ID.", required = true) String jvmId) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        return mcp.getAuditTarget(jvmId);
    }

    @Tool(
            description =
                    "Get a Target's DiscoveryNode lineage from historical database audit log. If"
                        + " the Cryostat instance has audit logging enabled, this tool can return"
                        + " information about the Target and all of its DiscoveryNode lineage"
                        + " ancestors up to (but excluding) the Universe node. Namespace parameter"
                        + " is required to identify the Cryostat instance managing the target.")
    public DiscoveryNode getAuditTargetLineage(
            @ToolArg(description = "The namespace of the Cryostat instance to query (required).")
                    String namespace,
            @ToolArg(description = "The Target's JVM hash ID.", required = true) String jvmId) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        return mcp.getAuditTargetLineage(jvmId);
    }

    @Tool(
            description =
                    "List the available JDK Flight Recorder Event Templates for a given Target."
                            + " Namespace parameter is required to identify the Cryostat instance"
                            + " managing the target.")
    public List<EventTemplate> listTargetEventTemplates(
            @ToolArg(description = "The namespace of the Cryostat instance to query (required).")
                    String namespace,
            @ToolArg(description = "The Target's ID.", required = true) long targetId) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        return mcp.listTargetEventTemplates(targetId);
    }

    @Tool(
            description =
                    "Get a specific .jfc (XML) JDK Flight Recorder Event Template definition."
                            + " Namespace parameter is required to identify the Cryostat instance"
                            + " managing the target.")
    public String getTargetEventTemplate(
            @ToolArg(description = "The namespace of the Cryostat instance to query (required).")
                    String namespace,
            @ToolArg(description = "The Target's ID.", required = true) long targetId,
            @ToolArg(description = "The event template's type.", required = true)
                    String templateType,
            @ToolArg(description = "The event template's name.", required = true)
                    String templateName) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        return mcp.getTargetEventTemplate(targetId, templateType, templateName);
    }

    @Tool(
            description =
                    "Get a list of active JDK Flight Recordings present in the Target JVM."
                            + " Namespace parameter is required to identify the Cryostat instance"
                            + " managing the target.")
    public List<RecordingDescriptor> listTargetActiveRecordings(
            @ToolArg(description = "The namespace of the Cryostat instance to query (required).")
                    String namespace,
            @ToolArg(description = "The Target's ID.", required = true) long targetId) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        return mcp.listTargetActiveRecordings(targetId);
    }

    @Tool(
            description =
                    "Get a list of archived JDK Flight Recordings sourced from the Target JVM."
                            + " Namespace parameter is required to identify the Cryostat instance"
                            + " managing the target.")
    public List<ArchivedRecordingDirectory> listTargetArchivedRecordings(
            @ToolArg(description = "The namespace of the Cryostat instance to query (required).")
                    String namespace,
            @ToolArg(description = "The Target's JVM hash ID.", required = true) String jvmId) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        return mcp.listTargetArchivedRecordings(jvmId);
    }

    @Tool(
            description =
                    "Start a new fixed-duration JDK Flight Recording on a Target JVM. When the"
                        + " recording completes, Cryostat will automatically capture the data and"
                        + " perform an automated analysis of its contents. Namespace parameter is"
                        + " required to identify the Cryostat instance managing the target.")
    public RecordingDescriptor startTargetRecording(
            @ToolArg(description = "The namespace of the Cryostat instance to query (required).")
                    String namespace,
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
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        return mcp.startTargetRecording(
                targetId, recordingName, templateName, templateType, duration);
    }

    @Tool(
            description =
                    "Scrape the Prometheus-formatted automated analysis metrics. Any recently"
                        + " processed automated analysis reports will appear here. These are raw"
                        + " scores. Use the JSON report output for human-readable explanations and"
                        + " suggestions. Scores of -1 indicate the JDK Flight Recorder event type"
                        + " required for this analysis was not configured. Scores of 0 indicate"
                        + " that no problem was detected. Scores of (0.0, 25.0) indicate that a low"
                        + " severity issue was detected. Scores of [25.0, 75.0) indicate that a"
                        + " medium severity issue was detected. Scores of [75.0, 100.0] indicate"
                        + " that a high severity issue was detected. Namespace parameter is"
                        + " required to identify the Cryostat instance managing the target.")
    public String scrapeMetrics(
            @ToolArg(description = "The namespace of the Cryostat instance to query (required).")
                    String namespace,
            @ToolArg(
                            description =
                                    "Filter results by a minimum score. If a Target has any rule"
                                        + " result greater than or equal to this value, then all"
                                        + " rule results for that Target will be included. For"
                                        + " example, setting this to 25.0 indicates that the"
                                        + " response should not include any results for Targets"
                                        + " which have no medium or high severity issues, and will"
                                        + " include all results for all Targets which have at least"
                                        + " one medium or high severity issue.",
                            required = true)
                    double minTargetScore) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        return mcp.scrapeMetrics(minTargetScore);
    }

    @Tool(
            description =
                    "Scrape the Prometheus-formatted automated analysis metrics for a specified"
                        + " Target. The most recently processed automated analysis report metrics"
                        + " for this target will be returned, if any are available. These are raw"
                        + " scores. Use the JSON report output for human-readable explanations and"
                        + " suggestions. Scores of -1 indicate the JDK Flight Recorder event type"
                        + " required for this analysis was not configured. Scores of 0 indicate"
                        + " that no problem was detected. Scores of (0.0, 25.0) indicate that a low"
                        + " severity issue was detected. Scores of [25.0, 75.0) indicate that a"
                        + " medium severity issue was detected. Scores of [75.0, 100.0] indicate"
                        + " that a high severity issue was detected. Namespace parameter is"
                        + " required to identify the Cryostat instance managing the target.")
    public String scrapeTargetMetrics(
            @ToolArg(description = "The namespace of the Cryostat instance to query (required).")
                    String namespace,
            @ToolArg(description = "The Target's JVM hash ID.", required = true) String jvmId) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        return mcp.scrapeTargetMetrics(jvmId);
    }

    @Tool(
            description =
                    "Get the JSON-formatted automated analysis report for a specified Target. The"
                        + " most recently processed automated analysis report document for this"
                        + " target will be returned, if any is available. This is a comprehensive"
                        + " report document containing human-readable explanations, summaries, and"
                        + " suggestions. For simple problem detection and incident reporting, use"
                        + " the Prometheus-format metrics scraping tools. Namespace parameter is"
                        + " required to identify the Cryostat instance managing the target.")
    public Object getTargetReport(
            @ToolArg(description = "The namespace of the Cryostat instance to query (required).")
                    String namespace,
            @ToolArg(description = "The Target's ID.", required = true) long targetId) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        return mcp.getTargetReport(targetId);
    }

    @Tool(
            description =
                    "Load an archived Flight Recording file into a database and execute an Apache"
                        + " Calcite SQL query against the contents of that file. The data is"
                        + " located in a schema named JFR, and each JFR event type is mapped to a"
                        + " table sharing the event's name. Each event attribute is mapped to a"
                        + " column of that table. The target's event templates can be inspected to"
                        + " determine which events may be found in a given recording. The JFR"
                        + " Calcite converter does NOT flatten nested fields like objectClass.name"
                        + " into separate top-level columns. All complex objects remain as single"
                        + " columns containing serialized structures. Fields like objectClass and"
                        + " eventThread are returned as formatted string representations of their"
                        + " internal structure, not as separate queryable columns. Queries cannot"
                        + " use \"objectClass\".\"name\", \"objectClass.name\", or"
                        + " \"objectClass\"['name'] syntax. Namespace parameter is required to"
                        + " identify the Cryostat instance managing the target.")
    public List<List<String>> executeQuery(
            @ToolArg(description = "The namespace of the Cryostat instance to query (required).")
                    String namespace,
            @ToolArg(description = "The Target's JVM hash ID.", required = true) String jvmId,
            @ToolArg(
                            description = "The name of the archived recording file to query.",
                            required = true)
                    String filename,
            @ToolArg(description = "The SQL query to execute.", required = true) String query) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        return mcp.executeQuery(jvmId, filename, query);
    }

    @Tool(
            description =
                    "Provides details about additional custom functions and structures available"
                            + " for SQL queries. Namespace parameter is required to identify the"
                            + " Cryostat instance managing the target.")
    public List<CryostatMCP.QueryExample> getQueryAdditionalFunctions(
            @ToolArg(description = "The namespace of the Cryostat instance to query (required).")
                    String namespace) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        return mcp.getQueryAdditionalFunctions();
    }

    @Tool(
            description =
                    "Provides example SQL queries as reference. Namespace parameter is required to"
                            + " identify the Cryostat instance managing the target.")
    public List<CryostatMCP.QueryExample> getQueryExamples(
            @ToolArg(description = "The namespace of the Cryostat instance to query (required).")
                    String namespace) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        return mcp.getQueryExamples();
    }
}
