package org.acme;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.acme.model.ArchivedRecordingDirectory;
import org.acme.model.DiscoveryNode;
import org.acme.model.EventTemplate;
import org.acme.model.Health;
import org.acme.model.RecordingDescriptor;
import org.acme.model.Target;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestForm;

@RegisterRestClient(
        configKey = "cryostat",
        baseUri = "http://localhost:8181" // this should always be overridden
        )
@ClientHeaderParam(name = "Authorization", value = "${cryostat.auth.value}")
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
    String scrapeMetrics();

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
