package org.acme;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.io.InputStream;
import java.util.List;
import org.acme.model.EventTemplate;
import org.acme.model.Health;
import org.acme.model.RecordingDescriptor;
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
    @Path("/api/v4/targets/{targetId}/event_templates")
    List<EventTemplate> targetEventTemplates(long targetId);

    @GET
    @Path("/api/v4/targets/{targetId}/event_templates/{templateType}/{templateName}")
    InputStream targetEventTemplate(long targetId, String templateType, String templateName);

    @GET
    @Path("/api/v4/targets/{targetId}/recordings")
    List<RecordingDescriptor> targetActiveRecordings(long targetId);

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
    String getTargetReport(long targetId);
}
