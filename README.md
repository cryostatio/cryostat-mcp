<a target="_blank" href="https://cryostat.io">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="./docs/images/cryostat_logo_hori_rgb_reverse.svg">
    <img src="./docs/images/cryostat_logo_hori_rgb_default.svg">
  </picture>
</a>

# Cryostat MCP

Model Context Protocol (MCP) servers for [Cryostat](https://cryostat.io).

## Project Structure

This is a multi-module Maven project containing two MCP server implementations:

### 1. cryostat-mcp (Single Instance)

A stdio-based MCP server that connects to a single Cryostat instance. Ideal for:
- Local development with a single Cryostat instance
- Direct integration with MCP clients like Claude Desktop
- Simple single-instance deployments

**Documentation**: See [cryostat-mcp-core/README.md](./cryostat-mcp-core/README.md)

### 2. k8s-multi-mcp (Kubernetes Multi-Instance)

An HTTP-based MCP server that acts as a multiplexing proxy for multiple Cryostat instances in a Kubernetes cluster. Features:
- Automatic discovery of Cryostat Custom Resources
- Namespace-based routing to appropriate Cryostat instances
- Credential forwarding for secure access
- Real-time CR change detection via Kubernetes Watch API

**Documentation**: See [k8s-multi-mcp/README.md](./k8s-multi-mcp/README.md)

## Quick Start

### Building the Entire Project

Build all modules:

```bash
./mvnw clean install
```

Build specific module:

```bash
# Single-instance MCP
./mvnw clean install -pl cryostat-mcp

# Multi-instance MCP
./mvnw clean install -pl k8s-multi-mcp
```

### Choosing the Right MCP

| Use Case | Recommended MCP |
|----------|----------------|
| Single Cryostat instance | cryostat-mcp |
| Multiple Cryostat instances in Kubernetes | k8s-multi-mcp |
| Local development | cryostat-mcp |
| Production Kubernetes deployment | k8s-multi-mcp |
| Claude Desktop integration | cryostat-mcp |
| Remote HTTP access | k8s-multi-mcp |

## SEE ALSO

* [cryostat.io](https://cryostat.io) : the self-hosted Java application monitoring and profiling tool which this MCP connects to.

## REQUIREMENTS

Build requirements:
- git
- JDK 17+
- Maven v3+

## BUILD

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

### BUILD THE MCP

The MCP server can be built as an all-in-one Java JAR using:

```bash
$ ./mvnw install
```

After this, the MCP can be run directly using `java -jar target/cryostat-mcp-*-runner.jar`. Additional configuration is required to hook up the MCP server to a particular Cryostat server instance.

## RUN

The easiest way to use the MCP is to provide an MCP configuration to your choice of compatible tool. LLM-enhanced code editors and development environments usually provide some way for you as a user to add custom MCP tools. The project's [mcp.json](./mcp.json) contains a template example you can use as a starting point.

Edit these environment variables to suit your Cryostat instance. You may need to replace the `https://localhost:8443` in the `REST_CLIENT` and `GRAPHQL_CLIENT` variables with a different host and/or port. You may also need to adjust the `CRYOSTAT_AUTH_VALUE` - this default example is using HTTP Basic authentication where the credentials are `user:pass`. This should be adjusted to suit what your Cryostat instance requires: either a different Basic base64-encoded credential, or a `Bearer abcd1234` auth token.

```json
{
    "QUARKUS_SMALLRYE_GRAPHQL_CLIENT_CRYOSTAT_URL": "https://localhost:8443/api/v4/graphql",
    "QUARKUS_REST_CLIENT_CRYOSTAT_URL": "https://localhost:8443",
    "CRYOSTAT_AUTH_VALUE": "Basic dXNlcjpwYXNz"
}
```

You should also consider enabling TLS verification. By default the example configuration disables TLS checks and will accept all certificates with no hostname verification, which is insecure. If your Cryostat instance presents a certificate with a correct hostname and signed by a trusted root CA then you should delete the following lines from your configuration.

```json
{
    "QUARKUS_TLS_NOTLS_TRUST_ALL": "true",
    "QUARKUS_REST_CLIENT_CRYOSTAT_VERIFY_HOST": "false",
    "QUARKUS_REST_CLIENT_CRYOSTAT_TLS_CONFIGURATION_NAME": "notls"
}
```
