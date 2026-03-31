# E2E Test Harness for cryostat-mcp-k8s-mux

This directory contains a complete end-to-end (e2e) test harness for the `cryostat-mcp-k8s-mux` project. It sets up a local OpenShift environment using CRC (CodeReady Containers/OpenShift Local) with multiple Cryostat instances deployed via the Cryostat Operator and sample applications to test the multi-instance MCP aggregation functionality.

## Overview

The e2e test environment creates:

- **CRC cluster**: OpenShift Local with native Routes for access
- **5 OpenShift projects (namespaces)**:
  - `cryostat-mcp`: cryostat-mcp-k8s-mux deployment
  - `c1`: Cryostat instance 1
  - `apps1`: Sample applications monitored by c1
  - `c2`: Cryostat instance 2
  - `apps2`: Sample applications monitored by c2
- **Infrastructure**:
  - cert-manager: Certificate management (v1.12.14)
  - Cryostat Operator: Manages Cryostat instances (v4.2.0-dev-ocp)
- **2 Cryostat instances** (via Custom Resources):
  - c1: Monitors `apps1` namespace (accessible via OpenShift Route)
  - c2: Monitors `apps2` namespace (accessible via OpenShift Route)
- **2 sample applications**:
  - Quarkus app with Cryostat agent in `apps1`
  - WildFly app with Cryostat agent in `apps2`
- **cryostat-mcp-k8s-mux**: Aggregates both Cryostat instances (accessible via OpenShift Route)

## Prerequisites

Before running the e2e tests, ensure you have the following tools installed:

- **CRC (CodeReady Containers)**: OpenShift Local (https://developers.redhat.com/products/openshift-local/overview)
- **oc**: OpenShift CLI (included with CRC)
- **kubectl**: Kubernetes CLI (https://kubernetes.io/docs/tasks/tools/)
- **Helm**: Kubernetes package manager (https://helm.sh/docs/intro/install/)
- **operator-sdk**: Operator SDK CLI (https://sdk.operatorframework.io/docs/installation/)
- **Docker/Podman**: Container runtime (CRC uses podman)
- **jq**: JSON processor (https://stedolan.github.io/jq/)

### CRC Configuration

Configure CRC with sufficient resources before starting:

```bash
crc config set cpus 6
crc config set memory 16384
crc config set disk-size 40
```

**Recommended CRC Configuration:**
- **CPUs**: 6 cores
- **Memory**: 16 GB (16384 MB)
- **Disk**: 40 GB

Then start CRC:

```bash
crc start
```

### System Requirements

- At least 16GB of total RAM (CRC will use 16GB)
- At least 50GB of free disk space (40GB for CRC + overhead)
- Linux, macOS, or WSL2 on Windows
- x86_64 architecture

## Quick Start

To set up the complete e2e environment, simply run:

```bash
cd cryostat-mcp-k8s-mux/e2e
./e2e.sh
```

This will execute all setup scripts in sequence and verify the deployment.

### Authentication

The e2e setup automatically obtains an OpenShift authentication token using `oc whoami --show-token` and configures the cryostat-mcp-k8s-mux deployment to use it for authenticating with Cryostat instances.

If you need to provide a custom token (e.g., for CI/CD environments), you can set the `OC_TOKEN` environment variable before running the setup:

```bash
export OC_TOKEN="your-custom-token-here"
./e2e.sh
```

The setup script will use your provided token instead of obtaining one from `oc`.

## Directory Structure

```
e2e/
├── e2e.sh                    # Main orchestrator script
├── README.md                 # This file
├── MIGRATION_SUMMARY.md      # Migration details from kind to CRC
├── OBSOLETE_FILES.md         # List of obsolete files from kind setup
├── manifests/                # Kubernetes/OpenShift manifests
│   ├── cryostat-c1.yaml      # Cryostat CR for instance 1
│   ├── cryostat-c2.yaml      # Cryostat CR for instance 2
│   ├── sample-app-apps1.yaml # Quarkus app deployment
│   └── sample-app-apps2.yaml # WildFly app deployment
├── helm-values/              # Helm values files
│   └── cryostat-mcp-k8s-mux-values.yaml    # cryostat-mcp-k8s-mux configuration
└── scripts/                  # Setup scripts
    ├── 01-verify-crc.sh      # Verify CRC is running
    ├── 02-install-cert-manager.sh # Install cert-manager
    ├── 03-install-cryostat-operator.sh # Install Cryostat Operator
    ├── 04-create-namespaces.sh # Create OpenShift projects
    ├── 05-deploy-cryostat-instances.sh # Deploy Cryostat CRs
    ├── 08-deploy-sample-apps.sh  # Deploy sample applications
    ├── 09-build-mcp-image.sh     # Build and push cryostat-mcp-k8s-mux image
    ├── 10-install-mcp.sh         # Install cryostat-mcp-k8s-mux
    ├── 11-verify.sh              # Verify all components
    └── cleanup.sh                # Cleanup script (no cluster deletion)
```

## Manual Step-by-Step Setup

If you prefer to run scripts individually or need to debug issues:

```bash
cd cryostat-mcp-k8s-mux/e2e/scripts

# 1. Verify CRC is running
./01-verify-crc.sh

# 2. Install cert-manager
./02-install-cert-manager.sh

# 3. Install Cryostat Operator
./03-install-cryostat-operator.sh

# 4. Create OpenShift projects
./04-create-namespaces.sh

# 5. Deploy Cryostat instances
./05-deploy-cryostat-instances.sh

# 6. Deploy sample applications
./08-deploy-sample-apps.sh

# 7. Build and push cryostat-mcp-k8s-mux image
./09-build-mcp-image.sh

# 8. Install cryostat-mcp-k8s-mux
./10-install-mcp.sh

# 9. Verify deployment
./11-verify.sh
```

## Accessing Components

Once the environment is set up, you can access components via OpenShift Routes:

### Get Route URLs

```bash
# Cryostat c1 route
oc get route -n c1 cryostat-c1 -o jsonpath='{.spec.host}'

# Cryostat c2 route
oc get route -n c2 cryostat-c2 -o jsonpath='{.spec.host}'

# cryostat-mcp-k8s-mux route
oc get route -n cryostat-mcp k8s-mux -o jsonpath='{.spec.host}'
```

### Cryostat Instances

- **Cryostat c1**: `https://cryostat-c1-c1.apps-crc.testing`
  - Monitors: `apps1` namespace
  - Sample app: quarkus-cryostat-agent
  - Auth: Basic (user:pass)
  
- **Cryostat c2**: `https://cryostat-c2-c2.apps-crc.testing`
  - Monitors: `apps2` namespace
  - Sample app: wildfly-cryostat-agent
  - Auth: Basic (user:pass)

### cryostat-mcp-k8s-mux

- **MCP Server**: `http://k8s-mux-cryostat-mcp.apps-crc.testing`
- **Health endpoint**: `http://k8s-mux-cryostat-mcp.apps-crc.testing/q/health`
- **Ready endpoint**: `http://k8s-mux-cryostat-mcp.apps-crc.testing/q/health/ready`

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
# cryostat-mcp-k8s-mux logs
kubectl logs -n cryostat-mcp -l app.kubernetes.io/name=cryostat-mcp-k8s-mux -f

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
kubectl get all -n cryostat-mcp
kubectl get all -n c1
kubectl get all -n c2
kubectl get all -n apps1
kubectl get all -n apps2
```

### Test MCP Endpoints

```bash
# Get MCP route
MCP_ROUTE=$(oc get route -n cryostat-mcp k8s-mux -o jsonpath='{.spec.host}')

# Health check
curl -k https://${MCP_ROUTE}/q/health

# Readiness check
curl -k https://${MCP_ROUTE}/q/health/ready

# Liveness check
curl -k https://${MCP_ROUTE}/q/health/live
```

### Verify Cryostat Discovery

Check that each Cryostat instance has discovered its respective sample application:

```bash
# Check c1 discovered targets (should see quarkus-cryostat-agent)
C1_ROUTE=$(oc get route -n c1 cryostat-c1 -o jsonpath='{.spec.host}')
curl -k -u user:pass https://${C1_ROUTE}/api/v3/targets

# Check c2 discovered targets (should see wildfly-cryostat-agent)
C2_ROUTE=$(oc get route -n c2 cryostat-c2 -o jsonpath='{.spec.host}')
curl -k -u user:pass https://${C2_ROUTE}/api/v3/targets
```

## Troubleshooting

For detailed troubleshooting information, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

### Common Issues

#### CRC Not Running

Ensure CRC is started and accessible:

```bash
crc status
crc start
```

#### Insufficient Resources

If pods are pending or failing due to resource constraints, check CRC configuration:

```bash
crc config view
```

Recommended settings:
```bash
crc config set cpus 6
crc config set memory 16384
crc config set disk-size 40
```

Then restart CRC:
```bash
crc stop
crc start
```

#### Pods Not Starting

Check pod status and events:

```bash
oc get pods -A
oc describe pod <pod-name> -n <namespace>
oc logs <pod-name> -n <namespace>
```

#### Image Pull Issues

If images fail to pull, check your internet connection. CRC uses the internal registry for the MCP image, but Cryostat and sample app images are pulled from external registries.

#### cert-manager Webhook Issues

If Cryostat CRs fail to deploy with webhook errors, ensure cert-manager is fully ready:

```bash
oc get pods -n cert-manager
oc get service cert-manager-webhook -n cert-manager
oc get endpoints cert-manager-webhook -n cert-manager
```

#### Operator Installation Issues

If the Cryostat operator fails to install, check OLM resources:

```bash
oc get csv -A | grep cryostat
oc get subscription -A | grep cryostat
oc get catalogsource -A | grep cryostat
```

## Cleanup

To clean up the e2e environment (without deleting the CRC cluster):

```bash
cd k8s-multi-mcp/e2e/scripts
./cleanup.sh
```

This will:
- Delete all e2e test resources (Cryostat instances, sample apps, MCP)
- Remove the Cryostat operator
- Remove cert-manager
- Clean up all created namespaces
- **Note**: CRC cluster itself is NOT deleted

To completely remove CRC:

```bash
crc stop
crc delete
```

## Customization

### Modifying Cryostat Configuration

Edit the Cryostat CR manifests in `manifests/`:
- `cryostat-c1.yaml`: Configure Cryostat c1
- `cryostat-c2.yaml`: Configure Cryostat c2

Then re-deploy:

```bash
./scripts/05-deploy-cryostat-instances.sh
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
│                    CRC (OpenShift Local)                     │
│                                                              │
│  ┌────────────────────┐  ┌────────────────────┐            │
│  │   Project: c1      │  │   Project: c2      │            │
│  │                    │  │                    │            │
│  │  ┌──────────────┐  │  │  ┌──────────────┐  │            │
│  │  │  Cryostat c1 │  │  │  │  Cryostat c2 │  │            │
│  │  │  (CR)        │  │  │  │  (CR)        │  │            │
│  │  └──────────────┘  │  │  └──────────────┘  │            │
│  │         │          │  │         │          │            │
│  │         │ monitors │  │         │ monitors │            │
│  │         ▼          │  │         ▼          │            │
│  └────────────────────┘  └────────────────────┘            │
│           │                        │                        │
│  ┌────────▼──────────┐  ┌─────────▼─────────┐             │
│  │  Project: apps1   │  │  Project: apps2   │             │
│  │                   │  │                   │             │
│  │  ┌─────────────┐  │  │  ┌─────────────┐  │             │
│  │  │ Quarkus App │  │  │  │ WildFly App │  │             │
│  │  │ (agent)     │  │  │  │ (agent)     │  │             │
│  │  └─────────────┘  │  │  └─────────────┘  │             │
│  └───────────────────┘  └───────────────────┘             │
│                                                              │
│  ┌──────────────────────────────────────────────┐          │
│  │      Project: cryostat-multi-mcp             │          │
│  │                                              │          │
│  │  ┌────────────────────────────────────────┐  │          │
│  │  │        k8s-multi-mcp                   │  │          │
│  │  │  (aggregates c1 and c2)                │  │          │
│  │  └────────────────────────────────────────┘  │          │
│  └──────────────────────────────────────────────┘          │
│                                                              │
│  ┌──────────────────────────────────────────────┐          │
│  │      Infrastructure                          │          │
│  │  - cert-manager (v1.12.14)                   │          │
│  │  - Cryostat Operator (v4.2.0-dev-ocp)        │          │
│  └──────────────────────────────────────────────┘          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
         │                │                │
         │ Route          │ Route          │ Route
         ▼                ▼                ▼
    cryostat-c1      cryostat-c2      k8s-multi-mcp
    .apps-crc        .apps-crc        .apps-crc
    .testing         .testing         .testing
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