# E2E Test Migration Summary: kind → CRC/OpenShift

## Overview

Successfully migrated the e2e test infrastructure from kind (Kubernetes in Docker) to CRC (CodeReady Containers / OpenShift Local). This migration enables more realistic testing in an OpenShift environment with native features like Routes and Operators.

## Key Changes

### Infrastructure

| Aspect | Before (kind) | After (CRC/OpenShift) |
|--------|---------------|----------------------|
| **Cluster** | kind cluster (ephemeral) | CRC (user-managed) |
| **Cryostat Deployment** | Helm charts | Cryostat Operator + CRs |
| **Authentication** | oauth2-proxy | openshift-oauth-proxy |
| **External Access** | Port forwarding | OpenShift Routes |
| **Namespaces** | kubectl namespaces | OpenShift Projects |
| **Prerequisites** | kind auto-installed | CRC pre-installed by user |

### New Components

1. **Operators**:
   - cert-manager (installed via manifest, not OLM)
   - Cryostat Operator 4.2.0-dev-ocp

2. **Custom Resources**:
   - `manifests/cryostat-c1.yaml` - Cryostat CR for c1 instance
   - `manifests/cryostat-c2.yaml` - Cryostat CR for c2 instance

3. **New Scripts**:
   - `01-verify-crc.sh` - Verify CRC is running
   - `02-install-cert-manager.sh` - Install cert-manager operator
   - `03-install-cryostat-operator.sh` - Install Cryostat operator
   - `05-deploy-cryostat-instances.sh` - Deploy Cryostat CRs

### Updated Components

1. **Scripts**:
   - `04-create-namespaces.sh` - Uses `oc new-project` instead of `kubectl create namespace`
   - `08-deploy-sample-apps.sh` - Uses `oc` instead of `kubectl`
   - `09-build-mcp-image.sh` - Builds for OpenShift registry
   - `10-install-mcp.sh` - Deploys with Route support
   - `11-verify.sh` - Checks Routes and tests connectivity
   - `cleanup.sh` - Cleans up projects without deleting CRC cluster
   - `e2e.sh` - Updated workflow and prerequisites

2. **Configuration**:
   - `helm-values/k8s-multi-mcp-values.yaml` - Configured for Routes instead of NodePort

### Removed Components

See [OBSOLETE_FILES.md](OBSOLETE_FILES.md) for the complete list of files to be removed.

## Architecture Comparison

### Before (kind)

```
┌─────────────────────────────────────────┐
│         kind Cluster (Docker)           │
│                                         │
│  ┌─────────┐  ┌─────────┐             │
│  │ Cryostat│  │ Cryostat│             │
│  │   c1    │  │   c2    │             │
│  │ (Helm)  │  │ (Helm)  │             │
│  └────┬────┘  └────┬────┘             │
│       │            │                   │
│  ┌────▼────┐  ┌───▼─────┐            │
│  │ apps1   │  │ apps2   │            │
│  └─────────┘  └─────────┘            │
│                                         │
│  ┌─────────────────────┐               │
│  │   k8s-multi-mcp     │               │
│  └─────────────────────┘               │
└─────────────────────────────────────────┘
         │ Port Forward
         ▼
    localhost:8080-8082
```

### After (CRC/OpenShift)

```
┌──────────────────────────────────────────┐
│      CRC / OpenShift Local               │
│                                          │
│  ┌──────────┐  ┌──────────┐            │
│  │ Cryostat │  │ Cryostat │            │
│  │    c1    │  │    c2    │            │
│  │   (CR)   │  │   (CR)   │            │
│  └─────┬────┘  └─────┬────┘            │
│        │             │                  │
│  ┌─────▼─────┐  ┌────▼─────┐          │
│  │  apps1    │  │  apps2   │          │
│  └───────────┘  └──────────┘          │
│                                          │
│  ┌──────────────────────┐               │
│  │  k8s-multi-mcp       │               │
│  └──────────────────────┘               │
│                                          │
│  ┌──────────────────────┐               │
│  │  OpenShift Routes    │               │
│  └──────────────────────┘               │
└──────────────────────────────────────────┘
         │ HTTPS Routes
         ▼
  *.apps-crc.testing
```

## Authentication Configuration

### Cryostat Instances

Both Cryostat instances use OpenShift OAuth proxy with Basic auth passthrough:

```yaml
securityOptions:
  authentication:
    openshift:
      enabled: true
      basicAuth:
        enabled: true
        secretName: cryostat-c1-basic-auth
        filename: htpasswd
```

Credentials: `user:pass` (bcrypt hashed in secrets)

### Access URLs

- **Cryostat c1**: `https://cryostat-c1-c1.apps-crc.testing`
- **Cryostat c2**: `https://cryostat-c2-c2.apps-crc.testing`
- **k8s-multi-mcp**: `http://k8s-multi-mcp-cryostat-multi-mcp.apps-crc.testing`

## Prerequisites

### System Requirements

- **RAM**: Minimum 9GB (12GB recommended)
- **CPU**: Minimum 4 cores (6 recommended)
- **Disk**: 35GB free space

### Required Tools

- **crc**: CodeReady Containers / OpenShift Local
- **oc**: OpenShift CLI
- **docker**: Container runtime
- **helm**: Kubernetes package manager
- **mvn**: Maven (for building k8s-multi-mcp)

## Workflow Changes

### Before (kind)

1. Install kind (if needed)
2. Create kind cluster
3. Load images into kind
4. Create namespaces
5. Add Helm repo
6. Install Cryostat c1 via Helm
7. Install Cryostat c2 via Helm
8. Deploy sample apps
9. Build MCP image
10. Install MCP via Helm
11. Verify
12. Port forward

### After (CRC/OpenShift)

1. Verify CRC is running
2. Install cert-manager operator
3. Install Cryostat operator
4. Create OpenShift projects
5. Deploy Cryostat CRs (c1 and c2)
6. Deploy sample apps
7. Build MCP image
8. Install MCP via Helm
9. Verify (including Routes)

## Benefits

1. **More Realistic**: Tests in actual OpenShift environment
2. **Native Features**: Uses OpenShift Routes, Projects, Operators
3. **Production-Like**: Matches real deployment scenarios
4. **Better Isolation**: OpenShift Projects provide stronger isolation
5. **Simpler Access**: Routes eliminate port-forwarding complexity
6. **Operator-Based**: Tests actual operator deployment path

## Testing

### Running E2E Tests

```bash
cd k8s-multi-mcp/e2e
./e2e.sh
```

### Manual Testing

```bash
# Check all Routes
oc get routes -A | grep -E '(cryostat|mcp)'

# Test Cryostat c1
curl -k -u user:pass https://cryostat-c1-c1.apps-crc.testing/health

# Test Cryostat c2
curl -k -u user:pass https://cryostat-c2-c2.apps-crc.testing/health

# Test k8s-multi-mcp
curl http://k8s-multi-mcp-cryostat-multi-mcp.apps-crc.testing/q/health
```

### Cleanup

```bash
cd k8s-multi-mcp/e2e/scripts
./cleanup.sh
```

## Post-Migration Fixes

After initial migration, four corrections were applied based on cryostat-operator CRD validation:

### 1. cert-manager Installation Method
**Issue**: Initially used OLM-based cert-manager operator installation
**Fix**: Changed to manifest-based installation matching cryostat-operator Makefile approach
**Rationale**: Simpler, more reliable, matches upstream deployment method
**File**: `scripts/02-install-cert-manager.sh`

```bash
# Now uses direct manifest application
CERT_MANAGER_VERSION="1.12.14"
CERT_MANAGER_MANIFEST="https://github.com/cert-manager/cert-manager/releases/download/v${CERT_MANAGER_VERSION}/cert-manager.yaml"
oc create --validate=false -f "${CERT_MANAGER_MANIFEST}"
```

### 2. Removed 'minimal' Property from Cryostat CRs
**Issue**: Cryostat CR manifests included `minimal: false` property
**Fix**: Removed property from both c1 and c2 manifests
**Rationale**: Property doesn't exist in v1beta2 CRD (only in deprecated v1beta1)
**Files**: `manifests/cryostat-c1.yaml`, `manifests/cryostat-c2.yaml`

### 3. Image Push to CRC Registry
**Issue**: Build script only built image locally, didn't push to CRC registry
**Fix**: Added registry login and push steps
**Rationale**: Helm deployment needs image in OpenShift internal registry
**File**: `scripts/09-build-mcp-image.sh`

```bash
# Now includes:
# 1. Tag image for registry
# 2. Login to OpenShift registry
# 3. Push image to registry
# 4. Verify ImageStream creation
```

### 4. Fixed Invalid Cryostat CR Properties
**Issue**: CR manifests used invalid properties not in v1beta2 CRD:
- `networkOptions.externalAccess` (doesn't exist)
- `securityOptions.authentication` (doesn't exist)

**Fix**: Simplified CRs to use only valid v1beta2 properties:
- Removed `networkOptions` entirely (Routes created automatically on OpenShift)
- Changed to `authorizationOptions.basicAuth` for authentication
- Kept only: `targetNamespaces`, `enableCertManager`, `authorizationOptions`, `storageOptions`, `resources`

**Rationale**:
- OpenShift operator automatically creates Routes when deployed on OpenShift
- `authorizationOptions` is the correct top-level property for authentication
- Simpler CR is more maintainable and follows operator defaults

**Files**: `manifests/cryostat-c1.yaml`, `manifests/cryostat-c2.yaml`

## Migration Checklist

- [x] Create CRC verification script
- [x] Create operator installation scripts
- [x] Create Cryostat CR manifests
- [x] Update namespace/project creation
- [x] Update sample app deployment
- [x] Update MCP build process
- [x] Update MCP deployment
- [x] Update verification script
- [x] Update cleanup script
- [x] Update main orchestrator
- [x] Document obsolete files
- [x] Fix cert-manager installation (manifest-based)
- [x] Remove 'minimal' property from Cryostat CRs
- [x] Fix image push to CRC registry
- [x] Validate and fix all Cryostat CR properties against v1beta2 CRD
- [ ] Remove obsolete files (see OBSOLETE_FILES.md)
- [ ] Update README.md
- [ ] Update TROUBLESHOOTING.md
- [ ] Test complete workflow

## Next Steps

1. **Remove Obsolete Files**: Execute removal command from OBSOLETE_FILES.md
2. **Update Documentation**: Update README.md and TROUBLESHOOTING.md
3. **Test End-to-End**: Run complete e2e workflow on CRC
4. **Validate**: Ensure all components work correctly with Routes
5. **Clean Up**: Remove OBSOLETE_FILES.md and this MIGRATION_SUMMARY.md after completion

## Notes

- CRC cluster is managed by the user and not created/destroyed by e2e scripts
- All services use HTTPS via OpenShift Routes (self-signed certificates)
- Basic auth credentials are consistent across all Cryostat instances (user:pass)
- The k8s-multi-mcp application already supports discovering Cryostat CRs via the Kubernetes API
- Sample applications remain unchanged (same images and configuration)

## Support

For issues or questions:
- Check TROUBLESHOOTING.md (to be updated)
- Review logs: `oc logs -n <namespace> <pod-name>`
- Check Route status: `oc get routes -A`
- Verify operator status: `oc get csv -A`