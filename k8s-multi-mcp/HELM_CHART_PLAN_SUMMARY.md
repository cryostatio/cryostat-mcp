# Helm Chart Migration - Plan Summary

## Executive Summary

This plan outlines the migration from Kustomize to Helm for the k8s-multi-mcp deployment. The Helm chart will provide easier customization of the authorization header and namespace, with additional support for OpenShift Routes.

## Key Requirements (from task)

✅ **Primary Requirements:**
1. Allow customization of Authorization header value
2. Allow customization of installation namespace
3. Nothing else needs to be configurable initially

✅ **Additional Requirement (from feedback):**
4. Optional OpenShift Route support for external access

## Proposed Solution

### Chart Location
```
k8s-multi-mcp/chart/
```

### Chart Structure
```
chart/
├── Chart.yaml                    # Metadata
├── values.yaml                   # Configuration
├── .helmignore                   # Exclusions
├── README.md                     # Documentation
└── templates/
    ├── _helpers.tpl              # Helper functions
    ├── NOTES.txt                 # Post-install notes
    ├── namespace.yaml            # Conditional
    ├── secret.yaml               # Conditional
    ├── serviceaccount.yaml       # RBAC
    ├── clusterrole.yaml          # RBAC
    ├── clusterrolebinding.yaml   # RBAC
    ├── deployment.yaml           # Main app
    ├── service.yaml              # Service
    └── route.yaml                # OpenShift (conditional)
```

## Configuration Parameters

### Required Parameters (User Must Provide)

**Authorization Header:**
```yaml
# Option 1: Create secret from value
auth:
  authorizationHeader: "Bearer my-token-value"

# Option 2: Reference existing secret
auth:
  existingSecret: "my-existing-secret"
  secretKey: "authorization-header"
```

### Optional Parameters (Have Defaults)

**Namespace:**
```yaml
namespace: cryostat-mcp-system  # Default
createNamespace: true            # Default
```

**OpenShift Route:**
```yaml
route:
  enabled: false                 # Default: disabled
  host: ""                       # Default: auto-generated
  tls:
    enabled: true                # Default: TLS enabled
    termination: edge            # Default: edge termination
    insecureEdgeTerminationPolicy: Redirect  # Default
```

**Image:**
```yaml
image:
  repository: quay.io/andrewazores/cryostat-k8s-multi-mcp
  tag: latest
  pullPolicy: Always
```

**Resources:**
```yaml
resources:
  requests:
    memory: "256Mi"
    cpu: "100m"
  limits:
    memory: "512Mi"
    cpu: "500m"
```

## Usage Examples

### Example 1: Minimal Installation (Kubernetes)
```bash
helm install k8s-multi-mcp ./k8s-multi-mcp/chart/ \
  --set auth.authorizationHeader="Bearer my-token"
```

**Result:**
- Installs in namespace: `cryostat-mcp-system`
- Creates secret with authorization header
- No external access (ClusterIP service only)

### Example 2: Custom Namespace
```bash
helm install k8s-multi-mcp ./k8s-multi-mcp/chart/ \
  --set namespace=my-namespace \
  --set auth.authorizationHeader="Bearer my-token"
```

**Result:**
- Installs in namespace: `my-namespace`
- Creates namespace if it doesn't exist

### Example 3: OpenShift with Route
```bash
helm install k8s-multi-mcp ./k8s-multi-mcp/chart/ \
  --set auth.authorizationHeader="Bearer my-token" \
  --set route.enabled=true \
  --set route.host=mcp.apps.my-cluster.example.com
```

**Result:**
- Installs in default namespace
- Creates OpenShift Route with custom hostname
- TLS enabled with edge termination

### Example 4: Using Existing Secret
```bash
# Pre-create secret
kubectl create secret generic my-secret \
  --from-literal=authorization-header="Bearer my-token" \
  -n my-namespace

# Install chart
helm install k8s-multi-mcp ./k8s-multi-mcp/chart/ \
  --set namespace=my-namespace \
  --set createNamespace=false \
  --set auth.existingSecret=my-secret
```

**Result:**
- Uses existing secret instead of creating one
- Namespace must already exist

## Secret Management Strategy

The chart supports **two modes** for secret management:

### Mode 1: Chart Creates Secret (Recommended for simplicity)
- User provides `auth.authorizationHeader` in values
- Chart creates secret: `{{ release-name }}-k8s-multi-mcp-credentials`
- Secret contains base64-encoded authorization header

**Pros:**
- Simple - one command installation
- Secret lifecycle managed by Helm

**Cons:**
- Sensitive value in values.yaml or command line
- Visible in Helm history

### Mode 2: User Provides Existing Secret (Recommended for security)
- User creates secret separately (e.g., from sealed secret, vault, etc.)
- User provides `auth.existingSecret` name
- Chart references the existing secret

**Pros:**
- More secure - secret managed outside Helm
- Compatible with GitOps workflows
- Secret not in Helm history

**Cons:**
- Requires separate secret creation step
- User must ensure secret exists before installation

## OpenShift Route Support

### Why OpenShift Routes?

OpenShift Routes provide a native way to expose services externally on OpenShift clusters, similar to Kubernetes Ingress but with additional features:

1. **Native Integration**: Built into OpenShift, no additional controllers needed
2. **TLS Termination**: Multiple termination types (edge, passthrough, reencrypt)
3. **Auto-generated Hostnames**: Can automatically generate hostnames
4. **Load Balancing**: Built-in load balancing across pods

### Route Configuration

**Basic Route (auto-generated hostname):**
```yaml
route:
  enabled: true
```

**Custom Hostname:**
```yaml
route:
  enabled: true
  host: mcp.apps.my-cluster.example.com
```

**TLS Configuration:**
```yaml
route:
  enabled: true
  tls:
    enabled: true
    termination: edge  # or passthrough, reencrypt
    insecureEdgeTerminationPolicy: Redirect  # or Allow, None
```

### Route Template Logic

```yaml
{{- if .Values.route.enabled }}
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: {{ include "k8s-multi-mcp.fullname" . }}
  namespace: {{ .Values.namespace }}
spec:
  host: {{ .Values.route.host | default "" }}
  to:
    kind: Service
    name: {{ include "k8s-multi-mcp.fullname" . }}
  port:
    targetPort: http
  {{- if .Values.route.tls.enabled }}
  tls:
    termination: {{ .Values.route.tls.termination }}
    insecureEdgeTerminationPolicy: {{ .Values.route.tls.insecureEdgeTerminationPolicy }}
  {{- end }}
{{- end }}
```

## Implementation Checklist

- [x] Analyze current kustomization structure
- [x] Document complete migration plan
- [ ] Create chart directory structure
- [ ] Create Chart.yaml
- [ ] Create values.yaml with all parameters
- [ ] Create _helpers.tpl with template functions
- [ ] Create namespace.yaml template
- [ ] Create secret.yaml template (conditional)
- [ ] Create serviceaccount.yaml template
- [ ] Create clusterrole.yaml template
- [ ] Create clusterrolebinding.yaml template
- [ ] Create deployment.yaml template
- [ ] Create service.yaml template
- [ ] Create route.yaml template (conditional, OpenShift)
- [ ] Create NOTES.txt
- [ ] Create .helmignore
- [ ] Create chart README.md
- [ ] Test chart rendering
- [ ] Verify namespace templating
- [ ] Verify secret configuration (both modes)
- [ ] Verify OpenShift Route configuration

## Testing Strategy

### 1. Template Rendering
```bash
# Test basic rendering
helm template k8s-multi-mcp ./k8s-multi-mcp/chart/ \
  --set auth.authorizationHeader="Bearer test"

# Test with custom namespace
helm template k8s-multi-mcp ./k8s-multi-mcp/chart/ \
  --set namespace=test-ns \
  --set auth.authorizationHeader="Bearer test"

# Test with OpenShift Route
helm template k8s-multi-mcp ./k8s-multi-mcp/chart/ \
  --set auth.authorizationHeader="Bearer test" \
  --set route.enabled=true
```

### 2. Dry Run Installation
```bash
helm install k8s-multi-mcp ./k8s-multi-mcp/chart/ \
  --dry-run --debug \
  --set auth.authorizationHeader="Bearer test"
```

### 3. Validation Checks
- ✅ All resources have correct namespace
- ✅ Secret is created with correct data (or existing secret referenced)
- ✅ Deployment references correct secret
- ✅ RBAC resources reference correct namespace
- ✅ All labels are consistent
- ✅ Route is only created when enabled
- ✅ Route has correct TLS configuration

## Benefits of This Approach

1. **Meets Requirements**: Addresses all specified requirements
2. **Flexible Secret Management**: Supports both creation and reference
3. **Platform Support**: Works on both Kubernetes and OpenShift
4. **Easy to Use**: Simple installation with sensible defaults
5. **Secure**: Supports external secret management
6. **Maintainable**: Clear structure and documentation
7. **Backward Compatible**: Kustomize files remain available

## Next Steps

Once you approve this plan, I will:

1. Switch to Code mode to implement the Helm chart
2. Create all chart files according to this specification
3. Test the chart rendering
4. Verify all configuration options work correctly
5. Provide installation instructions

## Questions for Review

1. ✅ Is the chart location (`k8s-multi-mcp/chart/`) acceptable?
2. ✅ Is the secret management approach (two modes) acceptable?
3. ✅ Are the default values appropriate?
4. ✅ Is the OpenShift Route configuration sufficient?
5. ❓ Should we include any additional configuration options now?
6. ❓ Should we add Ingress support for standard Kubernetes (in addition to Route)?

Please review this plan and let me know if you'd like any changes before I proceed with implementation!