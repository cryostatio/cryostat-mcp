# Helm Chart Migration Plan for k8s-multi-mcp

## Overview

This document outlines the plan to migrate the k8s-multi-mcp deployment from Kustomize to Helm. The Helm chart will provide a more user-friendly way to customize the deployment, particularly for the authorization header and namespace configuration.

## Current State Analysis

### Existing Kustomization Structure

The current setup uses Kustomize with the following resources:
- **Base directory**: `k8s-multi-mcp/src/main/kubernetes/`
  - `kustomization.yaml` - Main kustomization file
  - `namespace.yaml` - Creates `cryostat-mcp-system` namespace
  - `rbac.yaml` - ServiceAccount, ClusterRole, ClusterRoleBinding
  - `deployment.yaml` - Main application deployment
  - `service.yaml` - ClusterIP service exposing port 8080

- **Overlay directory**: `k8s-multi-mcp/src/main/kubernetes/overlay-k8s-mcp/`
  - `kustomization.yaml` - Overrides namespace to `k8s-mcp`

### Key Configuration Points

1. **Namespace**: Currently hardcoded in base (`cryostat-mcp-system`) with overlay option (`k8s-mcp`)
2. **Authorization Header**: Referenced from secret `k8s-multi-mcp-credentials` (key: `authorization-header`)
3. **Image**: `quay.io/andrewazores/cryostat-k8s-multi-mcp:latest`
4. **RBAC**: ClusterRole with permissions for Cryostat CRs and Services
5. **Resources**: 256Mi/100m requests, 512Mi/500m limits
6. **Service**: ClusterIP only - no external exposure configured

## Target Helm Chart Structure

```
k8s-multi-mcp/chart/
├── Chart.yaml                 # Chart metadata
├── values.yaml                # Default configuration values
├── .helmignore               # Files to exclude from packaging
├── README.md                 # Chart documentation
└── templates/
    ├── _helpers.tpl          # Template helper functions
    ├── NOTES.txt             # Post-installation notes
    ├── namespace.yaml        # Namespace (conditional)
    ├── secret.yaml           # Authorization secret (conditional)
    ├── serviceaccount.yaml   # ServiceAccount
    ├── clusterrole.yaml      # ClusterRole
    ├── clusterrolebinding.yaml # ClusterRoleBinding
    ├── deployment.yaml       # Deployment
    ├── service.yaml          # Service
    └── route.yaml            # OpenShift Route (conditional)
```

## Required Customization Parameters

### Primary Requirements (from task)

1. **Authorization Header** (`auth.authorizationHeader`)
   - Value to pass to sub-MCP instances
   - Used to create Kubernetes secret
   - Type: string (sensitive)

2. **Namespace** (`namespace`)
   - Namespace where chart is installed
   - Default: `cryostat-mcp-system`
   - Type: string

### Additional Configuration (for completeness)

3. **Image Configuration**
   - `image.repository`: Container image repository
   - `image.tag`: Image tag
   - `image.pullPolicy`: Pull policy (Always/IfNotPresent/Never)

4. **Secret Management**
   - `auth.existingSecret`: Reference to existing secret (alternative to creating one)
   - `auth.secretKey`: Key name in secret (default: `authorization-header`)

5. **Service Configuration**
   - `service.type`: Service type (ClusterIP/NodePort/LoadBalancer)
   - `service.port`: Service port (default: 8080)

6. **Resource Configuration**
   - `resources.requests.memory`: Memory request
   - `resources.requests.cpu`: CPU request
   - `resources.limits.memory`: Memory limit
   - `resources.limits.cpu`: CPU limit

7. **Replica Configuration**
   - `replicaCount`: Number of replicas (default: 1)

8. **OpenShift Route Configuration** (NEW)
   - `route.enabled`: Enable OpenShift Route for external access
   - `route.host`: Custom hostname for the route
   - `route.tls.enabled`: Enable TLS termination
   - `route.tls.termination`: TLS termination type (edge/passthrough/reencrypt)
   - `route.tls.insecureEdgeTerminationPolicy`: Policy for insecure traffic

## Implementation Details

### 1. Chart.yaml

```yaml
apiVersion: v2
name: k8s-multi-mcp
description: Kubernetes Multi-MCP for Cryostat - multiplexes MCP requests to multiple Cryostat instances
type: application
version: 1.0.0
appVersion: "1.0.0"
keywords:
  - cryostat
  - mcp
  - kubernetes
  - observability
maintainers:
  - name: Cryostat Team
```

### 2. values.yaml Structure

```yaml
# Namespace configuration
namespace: cryostat-mcp-system
createNamespace: true

# Authentication configuration
auth:
  # Authorization header value (creates secret if provided)
  authorizationHeader: ""
  # Existing secret name (alternative to authorizationHeader)
  existingSecret: ""
  # Key name in the secret
  secretKey: "authorization-header"

# Image configuration
image:
  repository: quay.io/andrewazores/cryostat-k8s-multi-mcp
  tag: latest
  pullPolicy: Always

# Service configuration
service:
  type: ClusterIP
  port: 8080

# Resource configuration
resources:
  requests:
    memory: "256Mi"
    cpu: "100m"
  limits:
    memory: "512Mi"
    cpu: "500m"

# Replica configuration
replicaCount: 1

# Additional environment variables
env:
  logLevel: INFO
  k8sLogLevel: DEBUG
  trustCerts: true
  trustAllTls: true

# OpenShift Route configuration (optional)
route:
  enabled: false
  host: ""  # Leave empty for auto-generated hostname
  tls:
    enabled: true
    termination: edge  # edge, passthrough, or reencrypt
    insecureEdgeTerminationPolicy: Redirect  # Redirect, Allow, or None
```

### 3. Template Helpers (_helpers.tpl)

Key helper functions to create:
- `k8s-multi-mcp.name`: Chart name
- `k8s-multi-mcp.fullname`: Full resource name
- `k8s-multi-mcp.chart`: Chart label
- `k8s-multi-mcp.labels`: Common labels
- `k8s-multi-mcp.selectorLabels`: Selector labels
- `k8s-multi-mcp.serviceAccountName`: ServiceAccount name
- `k8s-multi-mcp.secretName`: Secret name (created or existing)

### 4. Secret Management Logic

The chart will support two modes:

**Mode 1: Create Secret (default)**
```yaml
auth:
  authorizationHeader: "Bearer my-token-value"
```
- Chart creates secret with provided value
- Secret name: `{{ include "k8s-multi-mcp.fullname" . }}-credentials`

**Mode 2: Use Existing Secret**
```yaml
auth:
  existingSecret: "my-existing-secret"
  secretKey: "authorization-header"
```
- Chart references existing secret
- User must create secret before installation

### 5. Namespace Handling

The chart will:
- Use `{{ .Values.namespace }}` for all resource namespaces
- Conditionally create namespace if `createNamespace: true`
- Allow installation into existing namespace if `createNamespace: false`

### 6. RBAC Templating

Key changes:
- ServiceAccount namespace: `{{ .Values.namespace }}`
- ClusterRoleBinding subject namespace: `{{ .Values.namespace }}`
- All labels use helper templates

### 7. Deployment Templating

Key changes:
- Namespace: `{{ .Values.namespace }}`
- Image: `{{ .Values.image.repository }}:{{ .Values.image.tag }}`
- Secret reference: `{{ include "k8s-multi-mcp.secretName" . }}`
- Resources: From `{{ .Values.resources }}`
- Environment variables: Configurable via `{{ .Values.env }}`

## Migration Steps

1. ✅ Analyze current kustomization structure
2. ⏳ Create Helm chart directory structure
3. ⏳ Create Chart.yaml with metadata
4. ⏳ Create values.yaml with all configurable parameters
5. ⏳ Create _helpers.tpl with template functions
6. ⏳ Convert namespace.yaml to template
7. ⏳ Create secret.yaml template with conditional logic
8. ⏳ Convert rbac.yaml to three separate templates
9. ⏳ Convert deployment.yaml to template
10. ⏳ Convert service.yaml to template
11. ⏳ Create NOTES.txt with usage instructions
12. ⏳ Create .helmignore file
13. ⏳ Create README.md with documentation
14. ⏳ Test chart rendering
15. ⏳ Verify namespace templating
16. ⏳ Verify secret configuration

## Testing Strategy

### 1. Template Rendering Test
```bash
helm template k8s-multi-mcp ./k8s-multi-mcp/chart/ \
  --set auth.authorizationHeader="Bearer test-token" \
  --set namespace=test-namespace
```

### 2. Dry Run Installation
```bash
helm install k8s-multi-mcp ./k8s-multi-mcp/chart/ \
  --dry-run --debug \
  --set auth.authorizationHeader="Bearer test-token"
```

### 3. Validation Checks
- All resources have correct namespace
- Secret is created with correct data
- Deployment references correct secret
- RBAC resources reference correct namespace
- All labels are consistent

## Usage Examples

### Example 1: Basic Installation with Created Secret
```bash
helm install k8s-multi-mcp ./k8s-multi-mcp/chart/ \
  --set auth.authorizationHeader="Bearer my-token-value"
```

### Example 2: Custom Namespace
```bash
helm install k8s-multi-mcp ./k8s-multi-mcp/chart/ \
  --set namespace=my-custom-namespace \
  --set auth.authorizationHeader="Bearer my-token-value"
```

### Example 3: Using Existing Secret
```bash
# First create the secret
kubectl create secret generic my-cryostat-secret \
  --from-literal=authorization-header="Bearer my-token-value" \
  -n my-namespace

# Then install chart
helm install k8s-multi-mcp ./k8s-multi-mcp/chart/ \
  --set namespace=my-namespace \
  --set auth.existingSecret=my-cryostat-secret
```

### Example 4: Custom Image
```bash
helm install k8s-multi-mcp ./k8s-multi-mcp/chart/ \
  --set image.repository=my-registry/k8s-multi-mcp \
  --set image.tag=v1.2.3 \
  --set auth.authorizationHeader="Bearer my-token-value"
```

## Benefits Over Kustomize

1. **Simpler Configuration**: Single values.yaml file vs multiple patches
2. **Better Secret Management**: Built-in support for creating or referencing secrets
3. **Namespace Flexibility**: Easy to install in any namespace
4. **Version Management**: Helm tracks releases and enables rollbacks
5. **Templating Power**: More flexible than Kustomize patches
6. **Package Distribution**: Can be published to Helm repositories
7. **Dependency Management**: Can depend on other Helm charts if needed

## Backward Compatibility

The Kustomize files will remain in place for users who prefer that approach. The Helm chart provides an alternative deployment method with the same functionality but easier customization.

## Future Enhancements

Potential future additions (not in scope for initial migration):
- Ingress configuration
- NetworkPolicy templates
- PodDisruptionBudget
- HorizontalPodAutoscaler
- Additional environment variable configuration
- ConfigMap for application.properties
- Support for multiple replicas with leader election