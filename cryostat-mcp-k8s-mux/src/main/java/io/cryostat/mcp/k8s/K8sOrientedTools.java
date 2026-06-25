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

import java.text.ParseException;
import java.util.List;
import java.util.UUID;

import io.cryostat.mcp.CryostatMCP;
import io.cryostat.mcp.model.ArchivedRecordingDescriptor;
import io.cryostat.mcp.model.EventTemplate;
import io.cryostat.mcp.model.RecordingDescriptor;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.model.Duration;
import io.quarkiverse.mcp.server.MetaField;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class K8sOrientedTools {

    @Inject CryostatMCPInstanceManager instanceManager;
    @Inject PodNameResolver podNameResolver;

    @Tool(
            description =
                    "List the available JDK Flight Recorder Event Templates for a given"
                            + " application.")
    @MetaField(
            prefix = ToolLevelFilter.TOOL_LEVEL_META_PREFIX,
            name = ToolLevelFilter.TOOL_LEVEL_META_NAME,
            value = "HIGH")
    public List<EventTemplate> listEventTemplates(
            @ToolArg(description = "The namespace of application.", required = true)
                    String namespace,
            @ToolArg(description = "The podName of the application", required = true)
                    String podName) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        long targetId = podNameResolver.resolvePodNameToTargetId(namespace, podName);
        return mcp.listTargetEventTemplates(targetId);
    }

    @Tool(description = "Get a list of active JDK Flight Recordings present in the Target JVM.")
    @MetaField(
            prefix = ToolLevelFilter.TOOL_LEVEL_META_PREFIX,
            name = ToolLevelFilter.TOOL_LEVEL_META_NAME,
            value = "HIGH")
    public List<RecordingDescriptor> getActiveRecordingsList(
            @ToolArg(description = "The namespace of the application.", required = true)
                    String namespace,
            @ToolArg(description = "The podName of the application", required = true)
                    String podName) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        long targetId = podNameResolver.resolvePodNameToTargetId(namespace, podName);
        return mcp.listTargetActiveRecordings(targetId);
    }

    @Tool(description = "Start a Flight Recording on the given application.")
    @MetaField(
            prefix = ToolLevelFilter.TOOL_LEVEL_META_PREFIX,
            name = ToolLevelFilter.TOOL_LEVEL_META_NAME,
            value = "HIGH")
    public RecordingDescriptor startFlightRecording(
            @ToolArg(description = "The namespace of application.", required = true)
                    String namespace,
            @ToolArg(description = "The podName of the application", required = true)
                    String podName,
            @ToolArg(
                            description = "The Event Template to use for recording",
                            required = true,
                            defaultValue = "Continuous")
                    String eventTemplate,
            @ToolArg(
                            description =
                                    "The recording's duration. '0s' means continuous and ongoing,"
                                        + " otherwise a Kubernetes duration specifier can be"
                                        + " provided. Fixed-length recordings will be automatically"
                                        + " archived upon completion.",
                            required = true,
                            defaultValue = "0s")
                    String duration)
            throws ParseException, JsonProcessingException {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        long targetId = podNameResolver.resolvePodNameToTargetId(namespace, podName);
        String recordingName = UUID.randomUUID().toString();
        long durationSeconds = Duration.parse(duration).getDuration().getSeconds();
        return mcp.startTargetRecording(
                targetId, recordingName, eventTemplate, "TARGET", durationSeconds);
    }

    @Tool(
            description =
                    "Create a snapshot of the current active recording data in the Target JVM,"
                        + " archive it to persistent storage, and delete the intermediate active"
                        + " snapshot. Returns the descriptor of the resulting archived recording.")
    @MetaField(
            prefix = ToolLevelFilter.TOOL_LEVEL_META_PREFIX,
            name = ToolLevelFilter.TOOL_LEVEL_META_NAME,
            value = "HIGH")
    public ArchivedRecordingDescriptor archiveRecording(
            @ToolArg(description = "The namespace of the application.", required = true)
                    String namespace,
            @ToolArg(description = "The podName of the application.", required = true)
                    String podName) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        PodNameResolver.TargetInfo targetInfo = podNameResolver.resolveTarget(namespace, podName);
        return mcp.archiveTargetRecording(targetInfo.targetId(), targetInfo.jvmId());
    }

    @Tool(
            description =
                    "Get a list of archived Flight Recordings retrieved from the given target JVM.")
    @MetaField(
            prefix = ToolLevelFilter.TOOL_LEVEL_META_PREFIX,
            name = ToolLevelFilter.TOOL_LEVEL_META_NAME,
            value = "HIGH")
    public List<ArchivedRecordingDescriptor> getArchivedRecordingsList(
            @ToolArg(description = "The namespace of the application.", required = true)
                    String namespace,
            @ToolArg(description = "The podName of the application.", required = true)
                    String podName) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        String jvmId = podNameResolver.resolvePodNameToJvmId(namespace, podName);
        return mcp.listTargetArchivedRecordings(jvmId).stream()
                .flatMap(dir -> dir.recordings().stream())
                .toList();
    }
}
