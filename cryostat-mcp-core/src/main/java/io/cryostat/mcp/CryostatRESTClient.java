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

import java.util.List;

import io.cryostat.mcp.model.ArchivedRecordingDirectory;
import io.cryostat.mcp.model.DiscoveryNode;
import io.cryostat.mcp.model.EventTemplate;
import io.cryostat.mcp.model.Health;
import io.cryostat.mcp.model.RecordingDescriptor;
import io.cryostat.mcp.model.Target;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestQuery;

@RegisterRestClient(
        configKey = "cryostat",
        baseUri = "http://localhost:8181" // this should always be overridden
        )
@ClientHeaderParam(name = "Authorization", value = "${cryostat.auth.value:}", required = false)
public interface CryostatRESTClient {

    @GET
    @Path("/health")
    Health health();

    @GET
    @Path("/api/v4/discovery")
    DiscoveryNode getDiscoveryTree(@QueryParam("mergeRealms") boolean mergeRealms);

    @GET
    @Path("/api/beta/audit/targets/{jvmId}")
    Target auditTarget(String jvmId);

    @GET
    @Path("/api/beta/audit/target_lineage/{jvmId}")
    DiscoveryNode auditTargetLineage(String jvmId);

    @GET
    @Path("/api/v4/targets/{targetId}/event_templates")
    List<EventTemplate> targetEventTemplates(long targetId);

    @GET
    @Path("/api/v4/targets/{targetId}/event_templates/{templateType}/{templateName}")
    @Produces(MediaType.APPLICATION_XML)
    String targetEventTemplate(long targetId, String templateType, String templateName);

    @GET
    @Path("/api/v4/targets/{targetId}/recordings")
    List<RecordingDescriptor> targetActiveRecordings(long targetId);

    @GET
    @Path("/api/beta/fs/recordings/{jvmId}")
    List<ArchivedRecordingDirectory> targetArchivedRecordings(String jvmId);

    @POST
    @Path("/api/v4/targets/{targetId}/recordings")
    RecordingDescriptor startRecording(
            long targetId,
            @RestForm String recordingName,
            @RestForm String events,
            @RestForm long duration,
            @RestForm boolean toDisk,
            @RestForm String metadata,
            @RestForm boolean archiveOnStop);

    @GET
    @Path("/api/v4.1/metrics/reports")
    String scrapeMetrics(@RestQuery double minTargetScore);

    @GET
    @Path("/api/v4.1/metrics/reports/{jvmId}")
    String scrapeTargetMetrics(String jvmId);

    @GET
    @Path("/api/v4.1/targets/{targetId}/reports")
    @Produces(MediaType.APPLICATION_JSON)
    Object getTargetReport(long targetId);

    @POST
    @Path("/api/beta/recording_analytics/{jvmId}/{filename}")
    List<List<String>> executeQuery(
            String jvmId, String filename, @FormParam("query") String query);
}
