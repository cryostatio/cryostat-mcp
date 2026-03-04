package org.acme;

import org.acme.model.HealthResponse;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/cryostat")
public class CryostatResource {

    @Inject @RestClient CryostatClient cryostat;

    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public HealthResponse health() {
        return cryostat.health();
    }

}
