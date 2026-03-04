package org.acme;

import java.util.Map;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.quarkiverse.mcp.server.Tool;
import io.quarkus.qute.Qute;
import jakarta.inject.Inject;

public class CryostatMCP {

    @Inject @RestClient CryostatClient cryostat;

    @Tool(description = "Get Cryostat server health and configuration")
    String getHealth() {
        return Qute.fmt(
                """
                Cryostat server version: ${h.cryostatVersion}
                """,
                Map.of("h", cryostat.health())
                );
    }

}
