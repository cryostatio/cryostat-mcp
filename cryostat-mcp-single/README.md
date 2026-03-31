# cryostat-mcp-single

Standalone MCP (Model Context Protocol) server for a single Cryostat instance.

## Overview

This module provides a runnable MCP server that connects to a single Cryostat instance and exposes its functionality through MCP tools. It uses static authorization configured via environment variables.

## Architecture

- **Type**: Runnable application (produces uber JAR)
- **Protocol**: MCP over stdio
- **Authorization**: Static from environment variables
- **Dependencies**: Uses `cryostat-mcp-core` library for client implementations

## Configuration

Set the following environment variables:

- `CRYOSTAT_URL`: Base URL of the Cryostat instance (default: `http://localhost:8181`)
- `CRYOSTAT_AUTH`: Authorization header value (e.g., "Bearer token")
- `CRYOSTAT_GRAPHQL_PATH`: GraphQL endpoint path (default: `/api/v4/graphql`)

### URL Configuration

The GraphQL endpoint URL is automatically derived from the REST base URL:
- REST endpoint: `${CRYOSTAT_URL}`
- GraphQL endpoint: `${CRYOSTAT_URL}${CRYOSTAT_GRAPHQL_PATH}`

For example, if `CRYOSTAT_URL=http://localhost:8181`, the GraphQL endpoint will be `http://localhost:8181/api/v4/graphql`.

### Authorization

This module uses **static authorization** from the `CRYOSTAT_AUTH` environment variable for both REST and GraphQL clients. Both clients use the same authorization mechanism, ensuring consistent behavior across all tools.

- REST client: Uses `AuthorizationHeaderFactory` to inject the static authorization header
- GraphQL client: Uses `GraphQLClientFactory` to create clients with the static authorization header

## Building

```bash
mvn clean package
```

This produces an uber JAR at `target/quarkus-app/quarkus-run.jar`.

## Running

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

Or with environment variables:

```bash
CRYOSTAT_URL=https://my-cryostat:8181 java -jar target/quarkus-app/quarkus-run.jar
```

## Usage with MCP Clients

Configure your MCP client (e.g., Claude Desktop, Roo Cline) to use this server via stdio:

```json
{
  "mcpServers": {
    "cryostat": {
      "command": "java",
      "args": ["-jar", "/path/to/cryostat-mcp-single/target/quarkus-app/quarkus-run.jar"],
      "env": {
        "CRYOSTAT_URL": "http://localhost:8181"
      }
    }
  }
}
```

## Available Tools

All tools from `cryostat-mcp-core` are exposed with `@Tool` annotations:

- `getHealth` - Get Cryostat server health and configuration
- `getDiscoveryTree` - Get the full discovery tree of targets
- `listTargets` - List all discovered target applications
- `startTargetRecording` - Start a JFR recording on a target
- `scrapeMetrics` - Get Prometheus metrics
- And more...

## Differences from cryostat-mcp-k8s-mux

| Feature | cryostat-mcp-single | cryostat-mcp-k8s-mux |
|---------|---------------------|----------------------|
| Target | Single Cryostat instance | Multiple Cryostat instances in K8s |
| Protocol | stdio | HTTP/SSE |
| Authorization | Static from environment | Static from environment |
| Tool Registration | Automatic via CDI | Manual with namespace parameter |
| Deployment | Local/standalone | Kubernetes deployment |