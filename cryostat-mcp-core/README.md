# Cryostat MCP Core

A stdio-based Model Context Protocol (MCP) server that connects to a single Cryostat instance. This module provides direct integration with MCP clients like Claude Desktop for local development and single-instance deployments.

## Overview

### What is cryostat-mcp-core?

The cryostat-mcp-core module is a stdio-based MCP server that:

- **Connects** to a single Cryostat instance via REST and GraphQL APIs
- **Exposes** Cryostat functionality as MCP tools for AI assistants
- **Integrates** seamlessly with MCP clients like Claude Desktop
- **Provides** comprehensive access to JDK Flight Recorder data and analysis

### How It Works

The service communicates over stdio (standard input/output) using the MCP protocol:

1. **Client Connection**: MCP client (e.g., Claude Desktop) launches the server as a subprocess
2. **Tool Discovery**: Client queries available tools via MCP protocol
3. **Tool Execution**: Client invokes tools with parameters, server calls Cryostat APIs
4. **Response**: Server returns results in MCP format

```
┌─────────────────┐
│   MCP Client    │
│ (Claude Desktop)│
└────────┬────────┘
         │ stdio/MCP
         ▼
┌─────────────────┐
│ cryostat-mcp    │
│   (This Module) │
└────────┬────────┘
         │ REST/GraphQL + Auth
         ▼
┌─────────────────┐
│    Cryostat     │
│     Instance    │
└─────────────────┘
```

### Key Features

- **Comprehensive Tool Set**: 20+ tools covering discovery, recording, analysis, and querying
- **Dual API Support**: Uses both REST and GraphQL APIs for optimal performance
- **Automated Analysis**: Start recordings with automatic JFR analysis on completion
- **SQL Querying**: Execute Apache Calcite SQL queries against archived JFR data
- **Metrics Scraping**: Retrieve Prometheus-formatted analysis metrics
- **Audit Log Access**: Query historical target information from audit logs

## Prerequisites

### Required

- **Cryostat Instance**: A running Cryostat server (local or remote)
- **Java**: JDK 17+ for running the server
- **Authentication**: Valid credentials for the Cryostat instance

### For Building

- **Maven**: 3.8+
- **Java**: JDK 17+

### For MCP Client Integration

- **MCP Client**: Claude Desktop, Cline, or any MCP-compatible tool
- **Configuration**: Ability to add custom MCP servers to your client

## Building

### Build from Source

```bash
# From the cryostat-mcp-core directory
mvn clean package

# Output: target/cryostat-mcp-core-1.0.0-SNAPSHOT-runner.jar
```

### Build Native Image (Optional)

For faster startup and lower memory footprint:

```bash
mvn clean package -Pnative

# Output: target/cryostat-mcp-core-1.0.0-SNAPSHOT-runner
```

**Note**: Native builds require GraalVM or Mandrel.

## Configuration

### Environment Variables

Configure the server using environment variables:

| Variable | Required | Description | Example |
|----------|----------|-------------|---------|
| `QUARKUS_REST_CLIENT_CRYOSTAT_URL` | Yes | Cryostat REST API base URL | `https://localhost:8443` |
| `QUARKUS_SMALLRYE_GRAPHQL_CLIENT_CRYOSTAT_URL` | Yes | Cryostat GraphQL API URL | `https://localhost:8443/api/v4/graphql` |
| `CRYOSTAT_AUTH_VALUE` | Yes | Authorization header value | `Basic dXNlcjpwYXNz` or `Bearer <token>` |
| `QUARKUS_TLS_NOTLS_TRUST_ALL` | No | Disable TLS verification (dev only) | `true` |
| `QUARKUS_REST_CLIENT_CRYOSTAT_VERIFY_HOST` | No | Verify TLS hostname | `false` (dev only) |
| `QUARKUS_REST_CLIENT_CRYOSTAT_TLS_CONFIGURATION_NAME` | No | TLS configuration name | `notls` (dev only) |

### Authentication

The server supports two authentication methods:

#### HTTP Basic Authentication

```bash
# Encode credentials
echo -n "username:password" | base64
# Output: dXNlcm5hbWU6cGFzc3dvcmQ=

# Set environment variable
export CRYOSTAT_AUTH_VALUE="Basic dXNlcm5hbWU6cGFzc3dvcmQ="
```

#### Bearer Token Authentication

```bash
# Use token directly
export CRYOSTAT_AUTH_VALUE="Bearer your-token-here"
```

### TLS Configuration

#### Production (Recommended)

For production use with valid certificates:

```bash
export QUARKUS_REST_CLIENT_CRYOSTAT_URL="https://cryostat.example.com"
export QUARKUS_SMALLRYE_GRAPHQL_CLIENT_CRYOSTAT_URL="https://cryostat.example.com/api/v4/graphql"
# Do NOT set TLS_NOTLS_TRUST_ALL or VERIFY_HOST=false
```

#### Development (Insecure)

For local development with self-signed certificates:

```bash
export QUARKUS_REST_CLIENT_CRYOSTAT_URL="https://localhost:8443"
export QUARKUS_SMALLRYE_GRAPHQL_CLIENT_CRYOSTAT_URL="https://localhost:8443/api/v4/graphql"
export QUARKUS_TLS_NOTLS_TRUST_ALL="true"
export QUARKUS_REST_CLIENT_CRYOSTAT_VERIFY_HOST="false"
export QUARKUS_REST_CLIENT_CRYOSTAT_TLS_CONFIGURATION_NAME="notls"
```

**Warning**: Never use insecure TLS settings in production.

## Usage

### Running the Server

#### Direct Execution

```bash
# Set environment variables
export QUARKUS_REST_CLIENT_CRYOSTAT_URL="https://localhost:8443"
export QUARKUS_SMALLRYE_GRAPHQL_CLIENT_CRYOSTAT_URL="https://localhost:8443/api/v4/graphql"
export CRYOSTAT_AUTH_VALUE="Basic dXNlcjpwYXNz"

# Run the server
java -jar target/cryostat-mcp-core-1.0.0-SNAPSHOT-runner.jar
```

#### With Configuration File

Create a configuration file (e.g., `mcp-config.json`):

```json
{
  "mcpServers": {
    "cryostat": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/cryostat-mcp-core-1.0.0-SNAPSHOT-runner.jar"
      ],
      "env": {
        "QUARKUS_REST_CLIENT_CRYOSTAT_URL": "https://localhost:8443",
        "QUARKUS_SMALLRYE_GRAPHQL_CLIENT_CRYOSTAT_URL": "https://localhost:8443/api/v4/graphql",
        "CRYOSTAT_AUTH_VALUE": "Basic dXNlcjpwYXNz",
        "QUARKUS_TLS_NOTLS_TRUST_ALL": "true",
        "QUARKUS_REST_CLIENT_CRYOSTAT_VERIFY_HOST": "false",
        "QUARKUS_REST_CLIENT_CRYOSTAT_TLS_CONFIGURATION_NAME": "notls"
      }
    }
  }
}
```

### Integrating with Claude Desktop

Add to your Claude Desktop configuration (`~/Library/Application Support/Claude/claude_desktop_config.json` on macOS):

```json
{
  "mcpServers": {
    "cryostat": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/cryostat-mcp-core-1.0.0-SNAPSHOT-runner.jar"
      ],
      "env": {
        "QUARKUS_REST_CLIENT_CRYOSTAT_URL": "https://localhost:8443",
        "QUARKUS_SMALLRYE_GRAPHQL_CLIENT_CRYOSTAT_URL": "https://localhost:8443/api/v4/graphql",
        "CRYOSTAT_AUTH_VALUE": "Basic dXNlcjpwYXNz"
      }
    }
  }
}
```

Restart Claude Desktop to load the configuration.

## Available Tools

The server exposes the following MCP tools:

### Discovery & Health

- **getHealth**: Get Cryostat server health and configuration
- **getDiscoveryTree**: Get the full discovery tree of targets and environments
- **listTargets**: List discovered target applications with optional filtering

### Target Information

- **getAuditTarget**: Get target information from audit log (for lost targets)
- **getAuditTargetLineage**: Get target's discovery node lineage from audit log

### Event Templates

- **listTargetEventTemplates**: List available JFR event templates for a target
- **getTargetEventTemplate**: Get specific event template definition (XML)

### Recordings

- **listTargetActiveRecordings**: List active JFR recordings in a target JVM
- **listTargetArchivedRecordings**: List archived JFR recordings from a target
- **startTargetRecording**: Start a new fixed-duration JFR recording with auto-analysis

### Analysis & Metrics

- **scrapeMetrics**: Scrape Prometheus-formatted analysis metrics for all targets
- **scrapeTargetMetrics**: Scrape analysis metrics for a specific target
- **getTargetReport**: Get JSON-formatted automated analysis report for a target

### SQL Querying

- **executeQuery**: Execute Apache Calcite SQL query against archived JFR data
- **getQueryExamples**: Get example SQL queries for reference
- **getQueryAdditionalFunctions**: Get details about custom SQL functions

## Examples

### Example 1: List All Targets

```json
{
  "name": "listTargets",
  "arguments": {}
}
```

### Example 2: Start a Recording

```json
{
  "name": "startTargetRecording",
  "arguments": {
    "targetId": 123,
    "recordingName": "performance-analysis",
    "templateName": "Continuous",
    "templateType": "TARGET",
    "duration": 60
  }
}
```

### Example 3: Query Archived Recording

```json
{
  "name": "executeQuery",
  "arguments": {
    "jvmId": "abc123def456",
    "filename": "my-recording.jfr",
    "query": "SELECT COUNT(*) FROM jfr.\"jdk.ObjectAllocationSample\""
  }
}
```

### Example 4: Get Analysis Metrics

```json
{
  "name": "scrapeMetrics",
  "arguments": {
    "minTargetScore": 25.0
  }
}
```

This returns metrics for all targets with at least one medium or high severity issue.

### Example 5: Filter Targets by Label

```json
{
  "name": "listTargets",
  "arguments": {
    "labels": ["env=production", "team=backend"]
  }
}
```

## SQL Query Capabilities

The `executeQuery` tool allows executing Apache Calcite SQL queries against JFR data:

### Available Tables

Each JFR event type becomes a table in the `jfr` schema:
- `jfr."jdk.ObjectAllocationSample"`
- `jfr."jdk.ClassLoad"`
- `jfr."jdk.ThreadStart"`
- `jfr."jdk.ThreadEnd"`
- And many more...

### Custom Functions

- `CLASS_NAME(RecordedClass)`: Get fully-qualified class name
- `TRUNCATE_STACKTRACE(RecordedStackTrace, INT)`: Truncate stacktrace to depth
- `HAS_MATCHING_FRAME(RecordedStackTrace, VARCHAR)`: Check if stacktrace matches regex

### Example Queries

See the `getQueryExamples` tool for comprehensive examples including:
- Counting allocation events
- Finding top allocating stacktraces
- Analyzing class loading
- Thread lifecycle analysis

## Development

### Running in Dev Mode

```bash
# From cryostat-mcp-core directory
mvn quarkus:dev

# Server starts with hot reload enabled
```

### Running Tests

```bash
# Unit tests
mvn test

# All tests
mvn verify
```

### Project Structure

```
cryostat-mcp-core/
├── src/main/java/io/cryostat/mcp/
│   ├── CryostatMCP.java              # Main MCP tools implementation
│   ├── CryostatRESTClient.java       # REST API client interface
│   ├── CryostatGraphQLClient.java    # GraphQL API client interface
│   └── model/                        # Data models
│       ├── Target.java
│       ├── RecordingDescriptor.java
│       ├── DiscoveryNode.java
│       └── ...
├── src/main/resources/
│   └── application.properties        # Default configuration
├── src/test/java/                    # Tests
├── pom.xml                           # Maven configuration
└── README.md                         # This file
```

## Troubleshooting

### Connection Refused

**Problem**: Cannot connect to Cryostat instance

**Solutions**:
- Verify Cryostat is running: `curl -k https://localhost:8443/health`
- Check URL configuration matches your Cryostat deployment
- Verify network connectivity and firewall rules

### Authentication Failed

**Problem**: 401 Unauthorized errors

**Solutions**:
- Verify credentials are correct
- Check `CRYOSTAT_AUTH_VALUE` is properly base64-encoded for Basic auth
- Ensure token is valid and not expired for Bearer auth
- Test credentials directly: `curl -H "Authorization: $CRYOSTAT_AUTH_VALUE" https://localhost:8443/health`

### TLS Certificate Errors

**Problem**: SSL/TLS verification failures

**Solutions**:
- For development: Use insecure TLS settings (see Configuration section)
- For production: Ensure Cryostat certificate is signed by trusted CA
- Add Cryostat CA certificate to Java truststore if using custom CA

### Tool Execution Errors

**Problem**: Tools fail with unexpected errors

**Solutions**:
- Check Cryostat logs for API errors
- Verify target IDs and JVM IDs are correct
- Ensure target applications are still running
- Check that recordings exist before querying them

### High Memory Usage

**Problem**: Server consumes excessive memory

**Solutions**:
- Use native image build for lower memory footprint
- Adjust JVM heap settings: `java -Xmx256m -jar ...`
- Monitor with: `jcmd <pid> VM.native_memory summary`

## Comparison with k8s-multi-mcp

| Feature | cryostat-mcp-core | k8s-multi-mcp |
|---------|-------------------|---------------|
| **Protocol** | stdio | HTTP |
| **Instances** | Single | Multiple |
| **Use Case** | Local development, single instance | Kubernetes multi-tenant |
| **Client** | Claude Desktop, Cline | Remote HTTP clients |
| **Discovery** | Manual configuration | Automatic CR discovery |
| **Routing** | N/A | Namespace-based |
| **Deployment** | Local process | Kubernetes deployment |

## Contributing

This module is part of the Cryostat MCP project. See the parent [README](../README.md) for contribution guidelines.

## License

See the parent project for license information.

## Related Documentation

- [Parent README](../README.md) - Project overview and quick start
- [k8s-multi-mcp](../k8s-multi-mcp/README.md) - Multi-instance Kubernetes MCP
- [Cryostat Documentation](https://cryostat.io) - Cryostat user guide
- [Model Context Protocol](https://modelcontextprotocol.io/) - MCP specification
- [Quarkus](https://quarkus.io/) - Framework documentation