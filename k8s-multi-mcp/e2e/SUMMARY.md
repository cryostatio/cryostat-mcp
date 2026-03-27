# E2E Test Harness Summary

## Overview

This document provides a comprehensive summary of the e2e test harness for the cryostat-k8s-multi-mcp project, including architecture, implementation details, issues encountered, and solutions applied.

## Architecture

The test harness creates a complete multi-Cryostat environment for testing the k8s-multi-mcp aggregation service:

```
┌─────────────────────────────────────────────────────────────┐
│                    kind Cluster                              │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │  Namespace: cryostat-multi-mcp                     │    │
│  │  ┌──────────────────────────────────────────────┐  │    │
│  │  │  k8s-multi-mcp Service                       │  │    │
│  │  │  - Discovers Cryostat instances via CRDs    │  │    │
│  │  │  - Aggregates tools and resources           │  │    │
│  │  │  - Exposed on localhost:8082                │  │    │
│  │  └──────────────────────────────────────────────┘  │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌─────────────────────┐      ┌─────────────────────┐      │
│  │  Namespace: c1      │      │  Namespace: c2      │      │
│  │  ┌───────────────┐  │      │  ┌───────────────┐  │      │
│  │  │  Cryostat c1  │  │      │  │  Cryostat c2  │  │      │
│  │  │  v4.2.0       │  │      │  │  v4.2.0       │  │      │
│  │  │  oauth2-proxy │  │      │  │  oauth2-proxy │  │      │
│  │  │  user:pass    │  │      │  │  user:pass    │  │      │
│  │  │  localhost:   │  │      │  │  localhost:   │  │      │
│  │  │    8080       │  │      │  │    8081       │  │      │
│  │  └───────────────┘  │      │  └───────────────┘  │      │
│  │         ↓           │      │         ↓           │      │
│  │  Monitors: apps1    │      │  Monitors: apps2    │      │
│  └─────────────────────┘      └─────────────────────┘      │
│                                                              │
│  ┌─────────────────────┐      ┌─────────────────────┐      │
│  │  Namespace: apps1   │      │  Namespace: apps2   │      │
│  │  ┌───────────────┐  │      │  ┌───────────────┐  │      │
│  │  │  quarkus-app  │  │      │  │  wildfly-app  │  │      │
│  │  │  JMX: 9091    │  │      │  │  Agent: 9601  │  │      │
│  │  │  (jfr-jmx)    │  │      │  │               │  │      │
│  │  └───────────────┘  │      │  └───────────────┘  │      │
│  └─────────────────────┘      └─────────────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

## Components

### 1. Kind Cluster
- **Name:** `cryostat-mcp-e2e`
- **Configuration:** [`manifests/kind-config.yaml`](manifests/kind-config.yaml)
- **Features:**
  - Single control-plane node
  - Port mappings for ingress (80:80, 443:443)
  - Configured for local image loading

### 2. Namespaces
- `cryostat-multi-mcp` - k8s-multi-mcp service
- `c1` - First Cryostat instance
- `apps1` - Applications monitored by c1
- `c2` - Second Cryostat instance
- `apps2` - Applications monitored by c2

### 3. Cryostat Instances

#### Cryostat c1
- **Namespace:** `c1`
- **Version:** `4.2.0-snapshot`
- **Authentication:** oauth2-proxy with htpasswd (user:pass)
- **Target Namespaces:** `[apps1]`
- **Port:** localhost:8080 (via port-forward)
- **Values:** [`helm-values/cryostat-c1-values.yaml`](helm-values/cryostat-c1-values.yaml)

#### Cryostat c2
- **Namespace:** `c2`
- **Version:** `4.2.0-snapshot`
- **Authentication:** oauth2-proxy with htpasswd (user:pass)
- **Target Namespaces:** `[apps2]`
- **Port:** localhost:8081 (via port-forward)
- **Values:** [`helm-values/cryostat-c2-values.yaml`](helm-values/cryostat-c2-values.yaml)

### 4. Sample Applications

#### Quarkus Application (apps1)
- **Image:** `quay.io/redhat-java-monitoring/quarkus-cryostat-agent:latest`
- **Discovery:** JMX port 9091 named `jfr-jmx`
- **Manifest:** [`manifests/sample-app-apps1.yaml`](manifests/sample-app-apps1.yaml)

#### WildFly Application (apps2)
- **Image:** `quay.io/redhat-java-monitoring/wildfly-28-cryostat-agent:latest`
- **Discovery:** Cryostat Agent on port 9601
- **Manifest:** [`manifests/sample-app-apps2.yaml`](manifests/sample-app-apps2.yaml)

### 5. k8s-multi-mcp Service
- **Namespace:** `cryostat-multi-mcp`
- **Image:** `localhost/cryostat-k8s-multi-mcp:latest` (built locally)
- **Authentication:** Basic auth (user:pass)
- **Port:** localhost:8082 (via port-forward)
- **Values:** [`helm-values/k8s-multi-mcp-values.yaml`](helm-values/k8s-multi-mcp-values.yaml)
- **Chart:** [`../chart/`](../chart/)

## Scripts

The test harness consists of 13 modular bash scripts:

| Script | Purpose |
|--------|---------|
| [`01-setup-kind.sh`](scripts/01-setup-kind.sh) | Check/install kind CLI |
| [`02-create-cluster.sh`](scripts/02-create-cluster.sh) | Create kind cluster |
| [`03-load-images.sh`](scripts/03-load-images.sh) | Load container images into kind |
| [`04-create-namespaces.sh`](scripts/04-create-namespaces.sh) | Create all required namespaces |
| [`05-add-helm-repo.sh`](scripts/05-add-helm-repo.sh) | Add Cryostat Helm repository |
| [`06-install-cryostat-c1.sh`](scripts/06-install-cryostat-c1.sh) | Install Cryostat into c1 namespace |
| [`07-install-cryostat-c2.sh`](scripts/07-install-cryostat-c2.sh) | Install Cryostat into c2 namespace |
| [`08-deploy-apps.sh`](scripts/08-deploy-apps.sh) | Deploy sample applications |
| [`09-build-mcp-image.sh`](scripts/09-build-mcp-image.sh) | Build k8s-multi-mcp image |
| [`10-install-mcp.sh`](scripts/10-install-mcp.sh) | Install k8s-multi-mcp service |
| [`11-verify.sh`](scripts/11-verify.sh) | Verify all components are running |
| [`12-port-forward.sh`](scripts/12-port-forward.sh) | Set up port forwarding for testing |
| [`99-cleanup.sh`](scripts/99-cleanup.sh) | Delete kind cluster |

### Main Runner
- [`e2e.sh`](e2e.sh) - Orchestrates all setup steps in sequence

## Issues Encountered and Solutions

### 1. OAuth2-Proxy Permission Issue

**Problem:** oauth2-proxy container crashes with "permission denied" when reading htpasswd file.

**Root Cause:** Cryostat Helm chart creates htpasswd secrets with `defaultMode: 288` (octal 0440), but oauth2-proxy runs as non-root user (GID 65532) and cannot read the file.

**Solution:** Scripts 06 and 07 apply a JSON patch after Helm installation to update `defaultMode` to `292` (octal 0444), making the file world-readable:

```bash
VOLUME_INDEX=$(kubectl get deployment -n c1 cryostat-c1-v4 -o json | \
    jq '.spec.template.spec.volumes | map(.name) | index("cryostat-c1-htpasswd")')

kubectl patch deployment -n c1 cryostat-c1-v4 --type=json \
    -p="[{\"op\": \"replace\", \"path\": \"/spec/template/spec/volumes/$VOLUME_INDEX/secret/defaultMode\", \"value\": 292}]"
```

**Status:** ✅ Fixed in installation scripts

### 2. Maven Build Path Issue

**Problem:** Build script was looking for `mvnw` in wrong directory.

**Solution:** Corrected path navigation and used Maven reactor build:
```bash
cd "$PROJECT_ROOT"
./mvnw clean package -pl k8s-multi-mcp -am -DskipTests
```

**Status:** ✅ Fixed in [`09-build-mcp-image.sh`](scripts/09-build-mcp-image.sh)

### 3. Helm Values Structure Issue

**Problem:** Values file had `env` as array, but template expects `env.logLevel` and `env.k8sLogLevel` as simple values.

**Solution:** Restructured values file:
```yaml
env:
  logLevel: "INFO"
  k8sLogLevel: "INFO"
```

**Status:** ✅ Fixed in [`k8s-multi-mcp-values.yaml`](helm-values/k8s-multi-mcp-values.yaml)

### 4. Missing Secret Issue

**Problem:** k8s-multi-mcp pod in `CreateContainerConfigError` because Helm chart only creates credentials secret when `auth.authorizationHeader` is provided.

**Solution:** Added `auth.authorizationHeader` to values file:
```yaml
auth:
  authorizationHeader: "Basic dXNlcjpwYXNz"  # base64("user:pass")
```

**Status:** ✅ Fixed in [`k8s-multi-mcp-values.yaml`](helm-values/k8s-multi-mcp-values.yaml)

### 5. Namespace Mismatch Issue

**Problem:** Helm chart has default `namespace: cryostat-mcp-system` that overrides `--namespace` flag.

**Solution:** Added namespace override to values file and simplified installation:
```yaml
namespace: cryostat-multi-mcp
createNamespace: false
```

```bash
helm install "$RELEASE_NAME" "$CHART_DIR" \
    --namespace "$NAMESPACE" \
    --values "$VALUES_FILE" \
    --wait \
    --timeout 5m
```

**Status:** ✅ Fixed in [`10-install-mcp.sh`](scripts/10-install-mcp.sh) and values file

### 6. Verify Script Label Issue

**Problem:** Script used wrong label selector `app.kubernetes.io/name=k8s-multi-mcp` instead of `app.kubernetes.io/name=cryostat-k8s-multi-mcp`.

**Solution:** Fixed label selector in verification script.

**Status:** ✅ Fixed in [`11-verify.sh`](scripts/11-verify.sh)

### 7. Port Conflict Issue

**Problem:** Port-forward script fails when ports 8080, 8081, 8082 are already in use.

**Solution:** Added port availability check using `lsof`:
```bash
PORTS_IN_USE=()
if lsof -Pi :8080 -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    PORTS_IN_USE+=("8080")
fi
# ... check other ports ...

if [ ${#PORTS_IN_USE[@]} -gt 0 ]; then
    echo "✗ The following ports are already in use: ${PORTS_IN_USE[*]}"
    echo "Please free these ports or kill existing port-forwards:"
    echo "  pkill -f 'kubectl port-forward'"
    exit 1
fi
```

**Status:** ✅ Fixed in [`12-port-forward.sh`](scripts/12-port-forward.sh)

## Usage

### Quick Start

```bash
# Run complete e2e setup
./k8s-multi-mcp/e2e/e2e.sh

# Set up port forwarding for testing
./k8s-multi-mcp/e2e/scripts/12-port-forward.sh

# Access services
# - k8s-multi-mcp: http://localhost:8082
# - Cryostat c1: http://localhost:8080 (user:pass)
# - Cryostat c2: http://localhost:8081 (user:pass)

# Cleanup
./k8s-multi-mcp/e2e/scripts/99-cleanup.sh
```

### Individual Scripts

Each script can be run independently if needed:

```bash
# Create cluster only
./k8s-multi-mcp/e2e/scripts/02-create-cluster.sh

# Install just Cryostat c1
./k8s-multi-mcp/e2e/scripts/06-install-cryostat-c1.sh

# Verify deployment
./k8s-multi-mcp/e2e/scripts/11-verify.sh
```

## Testing

### Manual Testing

1. Run the complete e2e setup:
   ```bash
   ./k8s-multi-mcp/e2e/e2e.sh
   ```

2. Set up port forwarding:
   ```bash
   ./k8s-multi-mcp/e2e/scripts/12-port-forward.sh
   ```

3. Test k8s-multi-mcp service:
   ```bash
   curl http://localhost:8082/health
   ```

4. Test Cryostat instances:
   ```bash
   curl -u user:pass http://localhost:8080/health
   curl -u user:pass http://localhost:8081/health
   ```

5. Verify target discovery:
   ```bash
   # Check c1 discovers quarkus-app in apps1
   curl -u user:pass http://localhost:8080/api/v4/discovery

   # Check c2 discovers wildfly-app in apps2
   curl -u user:pass http://localhost:8081/api/v4/discovery
   ```

### Automated Testing

Future work could include:
- Integration tests using the deployed environment
- Automated verification of target discovery
- End-to-end MCP tool invocation tests
- Performance testing with multiple Cryostat instances

## Documentation

- [`README.md`](README.md) - Main documentation and usage guide
- [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) - Detailed troubleshooting guide
- [`SUMMARY.md`](SUMMARY.md) - This document

## Key Technical Concepts

### Kind (Kubernetes in Docker)
- Local Kubernetes cluster for testing
- Runs in Docker containers
- Supports loading local images without registry

### Cryostat
- Java application monitoring and profiling tool
- Uses JDK Flight Recorder (JFR)
- Discovers targets via JMX or Cryostat Agent
- Version 4.2.0-snapshot used in this harness

### OAuth2-Proxy
- Authentication proxy for Cryostat
- Supports htpasswd basic authentication
- Runs as non-root user (UID/GID 65532)

### k8s-multi-mcp
- MCP (Model Context Protocol) server
- Discovers Cryostat instances via Kubernetes CRDs
- Aggregates tools and resources from multiple instances
- Provides unified interface for AI assistants

### Helm
- Kubernetes package manager
- Used to install Cryostat and k8s-multi-mcp
- Supports values files for configuration

### JSON Patch
- Kubernetes patch type for direct value replacement
- Used to fix oauth2-proxy permission issue
- More precise than strategic merge patches

## Future Enhancements

1. **Automated Testing**
   - Add integration tests
   - Verify target discovery automatically
   - Test MCP tool invocations

2. **CI/CD Integration**
   - GitHub Actions workflow
   - Automated e2e testing on PRs
   - Performance benchmarking

3. **Additional Test Scenarios**
   - Multiple applications per namespace
   - Different JVM versions
   - Various JFR event configurations
   - Network policy testing

4. **Monitoring and Observability**
   - Prometheus metrics collection
   - Grafana dashboards
   - Log aggregation

5. **Documentation**
   - Video walkthrough
   - Architecture diagrams
   - API usage examples

## Contributing

When modifying the e2e test harness:

1. Keep scripts modular and focused on single tasks
2. Add error handling and validation
3. Update documentation (README.md, TROUBLESHOOTING.md)
4. Test changes with clean cluster (`99-cleanup.sh` then `e2e.sh`)
5. Document any new issues and solutions in TROUBLESHOOTING.md

## License

This test harness is part of the cryostat-mcp project and follows the same license.