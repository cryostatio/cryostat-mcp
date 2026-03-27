# E2E Test Harness for cryostat-k8s-multi-mcp

This directory contains a complete end-to-end (e2e) test harness for the `cryostat-k8s-multi-mcp` project. It sets up a local Kubernetes environment using kind (Kubernetes in Docker) with multiple Cryostat instances and sample applications to test the multi-instance MCP aggregation functionality.

## Overview

The e2e test environment creates:

- **kind cluster**: `cryostat-mcp-e2e` with port mappings for local access
- **5 namespaces**:
  - `cryostat-multi-mcp`: k8s-multi-mcp deployment
  - `c1`: Cryostat instance 1
  - `apps1`: Sample applications monitored by c1
  - `c2`: Cryostat instance 2
  - `apps2`: Sample applications monitored by c2
- **2 Cryostat instances**:
  - c1: Monitors `apps1` namespace (accessible at http://localhost:8080)
  - c2: Monitors `apps2` namespace (accessible at http://localhost:8081)
- **2 sample applications**:
  - Quarkus app with Cryostat agent in `apps1`
  - WildFly app with Cryostat agent in `apps2`
- **k8s-multi-mcp**: Aggregates both Cryostat instances (accessible at http://localhost:8082)

## Prerequisites

Before running the e2e tests, ensure you have the following tools installed:

- **Docker**: Container runtime (https://docs.docker.com/get-docker/)
- **kubectl**: Kubernetes CLI (https://kubernetes.io/docs/tasks/tools/)
- **Helm**: Kubernetes package manager (https://helm.sh/docs/intro/install/)
- **kind**: Will be installed automatically if missing (https://kind.sigs.k8s.io/)

### System Requirements

- At least 8GB of available RAM
- At least 20GB of free disk space
- Linux, macOS, or WSL2 on Windows

## Quick Start

To set up the complete e2e environment, simply run:

```bash
cd k8s-multi-mcp/e2e
./e2e.sh
```

This will execute all setup scripts in sequence and verify the deployment.

## Directory Structure

```
e2e/
├── e2e.sh                    # Main orchestrator script
├── README.md                 # This file
├── manifests/                # Kubernetes manifests
│   ├── kind-config.yaml      # kind cluster configuration
│   ├── sample-app-apps1.yaml # Quarkus app deployment
│   └── sample-app-apps2.yaml # WildFly app deployment
├── helm-values/              # Helm values files
│   ├── cryostat-c1-values.yaml      # Cryostat c1 configuration
│   ├── cryostat-c2-values.yaml      # Cryostat c2 configuration
│   └── k8s-multi-mcp-values.yaml    # k8s-multi-mcp configuration
└── scripts/                  # Setup scripts
    ├── 01-setup-kind.sh      # Install kind if needed
    ├── 02-create-cluster.sh  # Create kind cluster
    ├── 03-load-images.sh     # Pre-load container images
    ├── 04-create-namespaces.sh # Create required namespaces
    ├── 05-add-helm-repo.sh   # Add Cryostat Helm repository
    ├── 06-install-cryostat-c1.sh # Install Cryostat c1
    ├── 07-install-cryostat-c2.sh # Install Cryostat c2
    ├── 08-deploy-sample-apps.sh  # Deploy sample applications
    ├── 09-build-mcp-image.sh     # Build k8s-multi-mcp image
    ├── 10-install-mcp.sh         # Install k8s-multi-mcp
    ├── 11-verify.sh              # Verify all components
    └── cleanup.sh                # Teardown script
```

## Manual Step-by-Step Setup

If you prefer to run scripts individually or need to debug issues:

```bash
cd k8s-multi-mcp/e2e/scripts

# 1. Check/install kind
./01-setup-kind.sh

# 2. Create kind cluster
./02-create-cluster.sh

# 3. Load container images
./03-load-images.sh

# 4. Create namespaces
./04-create-namespaces.sh

# 5. Add Cryostat Helm repository
./05-add-helm-repo.sh

# 6. Install Cryostat c1
./06-install-cryostat-c1.sh

# 7. Install Cryostat c2
./07-install-cryostat-c2.sh

# 8. Deploy sample applications
./08-deploy-sample-apps.sh

# 9. Build k8s-multi-mcp image
./09-build-mcp-image.sh

# 10. Install k8s-multi-mcp
./10-install-mcp.sh

# 11. Verify deployment
./11-verify.sh
```

## Accessing Components

Once the environment is set up, you can access:

### Cryostat Instances

- **Cryostat c1**: http://localhost:8080
  - Monitors: `apps1` namespace
  - Sample app: quarkus-cryostat-agent
  
- **Cryostat c2**: http://localhost:8081
  - Monitors: `apps2` namespace
  - Sample app: wildfly-cryostat-agent

### k8s-multi-mcp

- **MCP Server**: http://localhost:8082
- **Health endpoint**: http://localhost:8082/q/health
- **Ready endpoint**: http://localhost:8082/q/health/ready

### Sample Applications

Both sample applications are configured with Cryostat agents using basic authentication (`user:pass`):

- **Quarkus app** (apps1):
  - JMX port: 9091 (named "jfr-jmx")
  - Agent port: 9977
  
- **WildFly app** (apps2):
  - Agent port: 9601

## Testing the Environment

### View Logs

```bash
# k8s-multi-mcp logs
kubectl logs -n cryostat-multi-mcp -l app.kubernetes.io/name=k8s-multi-mcp -f

# Cryostat c1 logs
kubectl logs -n c1 -l app.kubernetes.io/name=cryostat -f

# Cryostat c2 logs
kubectl logs -n c2 -l app.kubernetes.io/name=cryostat -f

# Quarkus app logs
kubectl logs -n apps1 -l app=quarkus-cryostat-agent -f

# WildFly app logs
kubectl logs -n apps2 -l app=wildfly-cryostat-agent -f
```

### Check Component Status

```bash
# All namespaces
kubectl get all -A | grep -E "(cryostat|apps1|apps2)"

# Specific namespace
kubectl get all -n cryostat-multi-mcp
kubectl get all -n c1
kubectl get all -n c2
kubectl get all -n apps1
kubectl get all -n apps2
```

### Test MCP Endpoints

```bash
# Health check
curl http://localhost:8082/q/health

# Readiness check
curl http://localhost:8082/q/health/ready

# Liveness check
curl http://localhost:8082/q/health/live
```

### Verify Cryostat Discovery

Check that each Cryostat instance has discovered its respective sample application:

```bash
# Check c1 discovered targets (should see quarkus-cryostat-agent)
kubectl exec -n c1 deployment/cryostat-c1 -- curl -s http://localhost:8181/api/v3/targets

# Check c2 discovered targets (should see wildfly-cryostat-agent)
kubectl exec -n c2 deployment/cryostat-c2 -- curl -s http://localhost:8181/api/v3/targets
```

## Troubleshooting

For detailed troubleshooting information, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

### Common Issues

#### Cluster Creation Fails

If cluster creation fails, ensure Docker is running and you have sufficient resources:

```bash
docker info
```

#### Pods Not Starting

Check pod status and events:

```bash
kubectl get pods -A
kubectl describe pod <pod-name> -n <namespace>
kubectl logs <pod-name> -n <namespace>
```

#### Image Pull Issues

If images fail to pull, check your internet connection and Docker Hub rate limits. You can manually pull images:

```bash
docker pull quay.io/cryostat/cryostat:4.2.0-SNAPSHOT
docker pull quay.io/redhat-java-monitoring/quarkus-cryostat-agent:latest
docker pull quay.io/redhat-java-monitoring/wildfly-28-cryostat-agent:latest
```

#### Port Conflicts

If ports 8080, 8081, or 8082 are already in use, you'll need to:

1. Stop the conflicting service, or
2. Modify the port mappings in `manifests/kind-config.yaml` and the corresponding Helm values files

#### Helm Installation Fails

Ensure the Helm repository is up to date:

```bash
helm repo update cryostat
helm search repo cryostat
```

## Cleanup

To completely remove the e2e environment:

```bash
cd k8s-multi-mcp/e2e/scripts
./cleanup.sh
```

This will:
- Delete the kind cluster and all its resources
- Optionally remove local Docker images

## Customization

### Modifying Cryostat Configuration

Edit the Helm values files in `helm-values/`:
- `cryostat-c1-values.yaml`: Configure Cryostat c1
- `cryostat-c2-values.yaml`: Configure Cryostat c2

Then re-run the installation script:

```bash
./scripts/06-install-cryostat-c1.sh  # or 07 for c2
```

### Modifying k8s-multi-mcp Configuration

Edit `helm-values/k8s-multi-mcp-values.yaml`, then:

```bash
./scripts/10-install-mcp.sh
```

### Adding More Sample Applications

1. Create a new manifest file in `manifests/`
2. Add deployment logic to `scripts/08-deploy-sample-apps.sh`
3. Update verification in `scripts/11-verify.sh`

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    kind Cluster                              │
│                                                              │
│  ┌────────────────────┐  ┌────────────────────┐            │
│  │   Namespace: c1    │  │   Namespace: c2    │            │
│  │                    │  │                    │            │
│  │  ┌──────────────┐  │  │  ┌──────────────┐  │            │
│  │  │  Cryostat c1 │  │  │  │  Cryostat c2 │  │            │
│  │  │  :8181       │  │  │  │  :8181       │  │            │
│  │  └──────────────┘  │  │  └──────────────┘  │            │
│  │         │          │  │         │          │            │
│  │         │ monitors │  │         │ monitors │            │
│  │         ▼          │  │         ▼          │            │
│  └────────────────────┘  └────────────────────┘            │
│           │                        │                        │
│  ┌────────▼──────────┐  ┌─────────▼─────────┐             │
│  │ Namespace: apps1  │  │ Namespace: apps2  │             │
│  │                   │  │                   │             │
│  │  ┌─────────────┐  │  │  ┌─────────────┐  │             │
│  │  │ Quarkus App │  │  │  │ WildFly App │  │             │
│  │  │ (agent)     │  │  │  │ (agent)     │  │             │
│  │  └─────────────┘  │  │  └─────────────┘  │             │
│  └───────────────────┘  └───────────────────┘             │
│                                                              │
│  ┌──────────────────────────────────────────────┐          │
│  │      Namespace: cryostat-multi-mcp           │          │
│  │                                              │          │
│  │  ┌────────────────────────────────────────┐  │          │
│  │  │        k8s-multi-mcp                   │  │          │
│  │  │  (aggregates c1 and c2)                │  │          │
│  │  │        :8080                           │  │          │
│  │  └────────────────────────────────────────┘  │          │
│  └──────────────────────────────────────────────┘          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
         │                │                │
         │ :8080          │ :8081          │ :8082
         ▼                ▼                ▼
    localhost:8080   localhost:8081   localhost:8082
```

## Contributing

When adding new features or modifying the e2e setup:

1. Update the relevant scripts in `scripts/`
2. Update configuration files in `manifests/` or `helm-values/`
3. Update this README with any new instructions
4. Test the complete flow with `./e2e.sh`

## Support

For issues or questions:
- Check the troubleshooting section above
- Review logs from the relevant components
- Consult the main project README at `../README.md`