package io.cryostat.mcp.model;

public record Health(
        String cryostatVersion,
        boolean dashboardConfigured,
        boolean dashboardAvailable,
        boolean datasourceConfigured,
        boolean datasourceAvailable,
        boolean reportsConfigured,
        boolean reportsAvailable,
        BuildInfo build) {}
