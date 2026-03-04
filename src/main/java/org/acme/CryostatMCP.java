package org.acme;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.acme.model.DiscoveryNode;
import org.acme.model.DiscoveryNodeFilter;
import org.acme.model.EventTemplate;
import org.acme.model.Health;
import org.acme.model.RecordingDescriptor;
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
                    Get a list of all discovered Target applications. Each Target belongs to a Discovery Node. In a Kubernetes context
                    the Discovery Node will be a Pod or equivalent object. Searching for the Target associated with a particular Pod
                    can be done by querying this endpoint with the Pod's name as a filter input. If no filter inputs are provided,
                    the full list of all discovered Targets will be returned. Otherwise, if any filter inputs are provided, then only
                    Targets which match all of the given inputs will be returned.
                    """)
    List<DiscoveryNode> listTargets(
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
                    "List the available JDK Flight Recorder Event Templates for a given Target.")
    List<EventTemplate> listTargetEventTemplates(
            @ToolArg(description = "The Target's ID.", required = true) long targetId) {
        return rest.targetEventTemplates(targetId);
    }

    @Tool(description = "Get a specific .jfc (XML) JDK Flight Recorder Event Template definition.")
    InputStream getTargetEventTemplate(
            @ToolArg(description = "The Target's ID.", required = true) long targetId,
            @ToolArg(description = "The event template's templateType.", required = true)
                    String templateType,
            @ToolArg(description = "The event template's name.", required = true)
                    String templateName) {
        return rest.targetEventTemplate(targetId, templateType, templateName);
    }

    @Tool(description = "Get a list of active JDK Flight Recordings present in the Target JVM.")
    List<RecordingDescriptor> listTargetActiveRecordings(
            @ToolArg(description = "The Target's ID.", required = true) long targetId) {
        return rest.targetActiveRecordings(targetId);
    }

    @Tool(
            description =
                    """
                    Start a new fixed-duration JDK Flight Recording on a Target JVM.
                    When the recording completes, Cryostat will automatically capture the data
                    and perform an automated analysis of its contents.
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
                    automated analysis reports will appear here.
                    Scores of -1 indicate the JDK Flight Recorder event type required for this analysis was not configured.
                    Scores of 0 indicate that no problem was detected.
                    Scores of (0.0, 25.0) indicate that a low severity issue was detected.
                    Scores of [25.0, 75.0) indicate that a medium severity issue was detected.
                    Scores of [75.0, 100.0] indicate that a high severity issue was detected.
                    """)
    String scrapeMetrics() {
        return rest.scrapeMetrics();
    }

    @Tool(
            description =
                    """
                    Scrape the Prometheus-formatted automated analysis metrics for a specified Target.
                    The most recently processed automated analysis report metrics for this target will be returned,
                    if any are available.
                    Scores of -1 indicate the JDK Flight Recorder event type required for this analysis was not configured.
                    Scores of 0 indicate that no problem was detected.
                    Scores of (0.0, 25.0) indicate that a low severity issue was detected.
                    Scores of [25.0, 75.0) indicate that a medium severity issue was detected.
                    Scores of [75.0, 100.0] indicate that a high severity issue was detected.
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
                    """)
    Object getTargetReport(
            @ToolArg(description = "The Target's ID.", required = true) long targetId) {
        return rest.getTargetReport(targetId);
    }
}
