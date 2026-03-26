# Cryostat Kubernetes Multi-MCP

A Model Context Protocol (MCP) server that acts as a multiplexing proxy for multiple Cryostat instances deployed in a Kubernetes cluster. It enables external MCP clients to interact with Cryostat instances using namespace-based routing, without needing to know specific Cryostat CR names.

## Overview

### What is k8s-multi-mcp?

The k8s-multi-mcp service is an HTTP-based MCP server that:

- **Discovers** Cryostat Custom Resources (CRs) deployed in a Kubernetes cluster
- **Routes** MCP tool requests to the appropriate Cryostat instance based on namespace
- **Multiplexes** access to multiple Cryostat instances through a single endpoint
- **Forwards** client credentials transparently to Cryostat instances

### How It Works

The service uses a composable architecture that reuses the existing `cryostat-mcp` module as a sub-component:

1. **Discovery**: Watches Kubernetes API for Cryostat CRs using the Fabric8 Kubernetes client
2. **Routing**: Maps target namespaces to Cryostat instances based on CR specifications
3. **Delegation**: Creates per-request `cryostat-mcp` instances with client credentials
4. **Forwarding**: Passes client Authorization headers to Cryostat instances

```
┌─────────────┐
│ MCP Client  │
└──────┬──────┘
       │ HTTP/MCP + Auth Header
       ▼
┌─────────────────────────────────┐
│   k8s-multi-mcp (This Service)  │
│  ┌───────────────────────────┐  │
│  │ Namespace-based Routing   │  │
│  └───────────┬───────────────┘  │
│              │                   │
│  ┌───────────▼───────────────┐  │
│  │ cryostat-mcp instances    │  │
│  │ (created per-request)     │  │
│  └───────────┬───────────────┘  │
└──────────────┼───────────────────┘
               │ Client Auth
       ┌───────┴────────┐
       ▼                ▼
┌─────────────┐  ┌─────────────┐
│ Cryostat 1  │  │ Cryostat 2  │
│ (prod-ns)   │  │ (dev-ns)    │
└─────────────┘  └─────────────┘
```

### Key Features

- **Namespace-Based Routing**: Automatically routes requests to the correct Cryostat instance based on target namespace
- **Credential Forwarding**: Transparently forwards client credentials to Cryostat instances (no privilege escalation)
- **Deterministic Tiebreaker**: When multiple Cryostat instances monitor the same namespace, selects alphabetically first CR name
- **Real-Time Discovery**: Uses Kubernetes Watch API for immediate CR change notifications
- **Tool Parity**: Inherits all tools from `cryostat-mcp` automatically
- **Security**: ServiceAccount only used for Kubernetes API access, not for Cryostat access

## Prerequisites

### Required

- **Kubernetes Cluster**: Version 1.19+ with Cryostat Operator installed
- **Cryostat CRs**: One or more Cryostat Custom Resources deployed
- **kubectl Access**: Cluster access for deployment and verification

### For Building

- **Maven**: 3.8+ 
- **Java**: 17+
- **Docker/Podman**: For building container images (optional)

### For Development

- **IDE**: IntelliJ IDEA, VS Code, or Eclipse with Java support
- **Quarkus CLI**: For dev mode (optional but recommended)

## Building

### Build from Source

```bash
# From the k8s-multi-mcp directory
mvn clean package

# Output: target/quarkus-app/
```

### Build Native Image (Optional)

For faster startup and lower memory footprint:

```bash
mvn clean package -Pnative

# Output: target/k8s-multi-mcp-1.0.0-SNAPSHOT-runner
```

**Note**: Native builds require GraalVM or Mandrel.

### Build Container Image

```bash
# JVM mode
mvn clean package -Dquarkus.container-image.build=true

# Native mode
mvn clean package -Pnative -Dquarkus.container-image.build=true

# Push to registry
mvn clean package -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.push=true \
  -Dquarkus.container-image.registry=quay.io \
  -Dquarkus.container-image.group=your-org \
  -Dquarkus.container-image.name=k8s-multi-mcp \
  -Dquarkus.container-image.tag=latest
```

## Deployment

### Quick Start

Deploy to your Kubernetes cluster using the provided manifests:

```bash
# Create namespace
kubectl create namespace cryostat-mcp-system

# Apply all manifests
kubectl apply -k src/main/kubernetes/

# Verify deployment
kubectl get pods -n cryostat-mcp-system
kubectl logs -n cryostat-mcp-system deployment/k8s-multi-mcp
```

### RBAC Requirements

The service requires a ServiceAccount with permissions to:

- **List/Watch Cryostat CRs**: `operator.cryostat.io/v1beta2` resources
- **List Services**: For Cryostat service discovery

**Important**: The ServiceAccount is ONLY used for Kubernetes API access. Client credentials are used for all Cryostat access.

See `src/main/kubernetes/rbac.yaml` for the complete RBAC configuration.

### Configuration Options

Configure via environment variables in the Deployment:

| Variable | Default | Description |
|----------|---------|-------------|
| `QUARKUS_HTTP_PORT` | `8080` | HTTP port for MCP server |
| `QUARKUS_LOG_LEVEL` | `INFO` | Global log level |
| `QUARKUS_LOG_CATEGORY__IO_CRYOSTAT_MCP_K8S__LEVEL` | `DEBUG` | k8s-multi-mcp log level |

### Customizing the Deployment

Edit `src/main/kubernetes/kustomization.yaml` to customize:

- Namespace
- Resource limits
- Replica count
- Image name/tag
- Labels and annotations

Then apply with:

```bash
kubectl apply -k src/main/kubernetes/
```

## Usage

### Connecting MCP Clients

Connect your MCP client to the service endpoint:

```bash
# Port-forward for local testing
kubectl port-forward -n cryostat-mcp-system service/k8s-multi-mcp 8080:8080

# Client connects to http://localhost:8080
```

For production, expose via LoadBalancer or Ingress:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: k8s-multi-mcp
  namespace: cryostat-mcp-system
spec:
  type: LoadBalancer  # or use Ingress
  ports:
    - port: 8080
      targetPort: 8080
  selector:
    app: k8s-multi-mcp
```

### Authentication

All requests must include an `Authorization` header:

```http
POST /mcp HTTP/1.1
Host: k8s-multi-mcp.cryostat-mcp-system:8080
Authorization: Bearer <your-token>
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "listTargets",
    "arguments": {
      "namespace": "prod-app-1"
    }
  }
}
```

The service forwards this header to the appropriate Cryostat instance.

### Tool Calls with Namespace Parameters

#### Directed Operations (Require Namespace)

Operations that target a specific Cryostat instance require the `namespace` parameter:

```json
{
  "name": "startTargetRecording",
  "arguments": {
    "namespace": "prod-app-1",
    "targetId": 123,
    "recordingName": "my-recording",
    "templateName": "Continuous",
    "templateType": "TARGET",
    "duration": 60
  }
}
```

#### Non-Directed Operations (Optional Namespace)

Some operations can work across all instances or be filtered by namespace:

```json
{
  "name": "scrapeMetrics",
  "arguments": {
    "namespace": "prod-app-1",  // Optional: filter to specific namespace
    "minTargetScore": 0.0
  }
}
```

If `namespace` is omitted, the service aggregates results from all Cryostat instances.

### Error Handling

#### No Matching Instance

```json
{
  "error": {
    "code": "NO_MATCHING_INSTANCE",
    "message": "No Cryostat instance found monitoring namespace 'unknown-ns'",
    "details": {
      "requestedNamespace": "unknown-ns",
      "availableNamespaces": ["prod-app-1", "dev-app-1"]
    }
  }
}
```

**Resolution**: Check that a Cryostat CR exists with the namespace in its `spec.targetNamespaces`.

#### Multiple Matches (Warning)

```json
{
  "warning": {
    "code": "MULTIPLE_MATCHES",
    "message": "Multiple instances monitor namespace 'shared-ns', selected 'cryostat-alpha'",
    "details": {
      "namespace": "shared-ns",
      "selected": "cryostat-alpha",
      "alternatives": ["cryostat-beta"]
    }
  }
}
```

**Resolution**: This is informational. The service applies alphabetical tiebreaker. To control selection, rename CRs or adjust `targetNamespaces`.

#### Authentication Failed

```json
{
  "error": {
    "code": "AUTHENTICATION_FAILED",
    "message": "Authentication failed for Cryostat instance 'cryostat-prod'",
    "details": {
      "instance": "cryostat-prod",
      "statusCode": 401
    }
  }
}
```

**Resolution**: Verify your Authorization header is valid for the target Cryostat instance.

## Development

### Running Locally

For development, run in Quarkus dev mode:

```bash
# From k8s-multi-mcp directory
mvn quarkus:dev

# Service starts on http://localhost:8080
# Dev UI available at http://localhost:8080/q/dev
```

**Note**: Requires access to a Kubernetes cluster with Cryostat CRs. Configure kubeconfig:

```bash
export KUBECONFIG=/path/to/kubeconfig
mvn quarkus:dev
```

### Running Tests

```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# With coverage
mvn verify -Pcoverage
```

### Project Structure

```
k8s-multi-mcp/
├── src/main/java/io/cryostat/mcp/k8s/
│   ├── K8sMultiMCP.java                    # Main MCP tools wrapper
│   ├── CryostatInstanceDiscovery.java      # CR discovery via Watch API
│   ├── CryostatMCPInstanceManager.java     # Sub-MCP instance management
│   ├── ClientCredentialsContext.java       # Thread-local credential storage
│   └── model/
│       ├── Cryostat.java                   # Cryostat CR model
│       ├── CryostatSpec.java               # CR spec model
│       ├── CryostatStatus.java             # CR status model
│       └── CryostatInstance.java           # Internal instance representation
├── src/main/resources/
│   └── application.properties              # Quarkus configuration
├── src/main/kubernetes/                    # Kubernetes manifests
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── rbac.yaml
│   └── kustomization.yaml
├── src/test/java/                          # Tests
├── pom.xml                                 # Maven configuration
├── README.md                               # This file
└── EXAMPLES.md                             # Usage examples
```

### Key Components

- **K8sMultiMCP**: Wraps `cryostat-mcp` tools with namespace routing
- **CryostatInstanceDiscovery**: Watches Kubernetes for Cryostat CRs and builds namespace mappings
- **CryostatMCPInstanceManager**: Creates per-request `cryostat-mcp` instances with client credentials
- **ClientCredentialsContext**: Stores client Authorization header in ThreadLocal for request duration

## Troubleshooting

### Service Not Discovering CRs

Check RBAC permissions:

```bash
kubectl auth can-i list cryostats.operator.cryostat.io \
  --as=system:serviceaccount:cryostat-mcp-system:k8s-multi-mcp
```

Check logs:

```bash
kubectl logs -n cryostat-mcp-system deployment/k8s-multi-mcp
```

### Routing to Wrong Instance

Verify CR `targetNamespaces`:

```bash
kubectl get cryostats.operator.cryostat.io -A -o yaml | grep -A 5 targetNamespaces
```

Check service logs for routing decisions:

```bash
kubectl logs -n cryostat-mcp-system deployment/k8s-multi-mcp | grep "Routing"
```

### Connection Refused to Cryostat

Verify Cryostat service exists:

```bash
kubectl get svc -n <cryostat-namespace>
```

Check network policies allow traffic from k8s-multi-mcp namespace.

### High Memory Usage

Consider using native image build for lower memory footprint:

```bash
mvn clean package -Pnative -Dquarkus.container-image.build=true
```

Update deployment to use native image.

## Examples

See [EXAMPLES.md](EXAMPLES.md) for detailed usage examples including:

- Listing targets in a specific namespace
- Starting recordings
- Querying archived recordings
- Scraping metrics
- Handling errors

## Contributing

This module is part of the Cryostat MCP project. See the parent [README](../README.md) for contribution guidelines.

## License

See the parent project for license information.

## Related Documentation

- [Architecture](../../ARCHITECTURE.md) - Detailed architecture and design
- [API Specification](../../API_SPECIFICATION.md) - Complete API reference
- [Implementation Plan](../../IMPLEMENTATION_PLAN.md) - Development roadmap
- [Cryostat Operator](https://github.com/cryostatio/cryostat-operator) - Kubernetes operator for Cryostat
- [Model Context Protocol](https://modelcontextprotocol.io/) - MCP specification