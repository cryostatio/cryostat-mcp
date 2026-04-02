# cryostat-k8s-multi-mcp Helm Chart

This Helm chart deploys the Kubernetes Multi-MCP for Cryostat, which multiplexes MCP requests to multiple Cryostat instances discovered in a Kubernetes cluster.

## Prerequisites

- Kubernetes 1.19+
- Helm 3.0+
- Cryostat Operator installed in the cluster (for discovering Cryostat instances)

## Installing the Chart

### Basic Installation

Install the chart with a release name `my-mcp`:

```bash
helm install my-mcp ./k8s-multi-mcp/chart/ \
  --set auth.authorizationHeader="Bearer your-token-value"
```

### Custom Namespace

Install into a custom namespace:

```bash
helm install my-mcp ./k8s-multi-mcp/chart/ \
  --set namespace=my-namespace \
  --set auth.authorizationHeader="Bearer your-token-value"
```

### Using an Existing Secret

If you prefer to manage the secret separately (recommended for production):

```bash
# Create the secret first
kubectl create secret generic my-cryostat-secret \
  --from-literal=authorization-header="Bearer your-token-value" \
  -n cryostat-mcp-system

# Install the chart
helm install my-mcp ./k8s-multi-mcp/chart/ \
  --set auth.existingSecret=my-cryostat-secret
```

### OpenShift with Route

Enable external access via OpenShift Route:

```bash
helm install my-mcp ./k8s-multi-mcp/chart/ \
  --set auth.authorizationHeader="Bearer your-token-value" \
  --set route.enabled=true \
  --set route.host=mcp.apps.my-cluster.example.com
```

### Kubernetes with Ingress

Enable external access via Kubernetes Ingress:

```bash
helm install my-mcp ./k8s-multi-mcp/chart/ \
  --set auth.authorizationHeader="Bearer your-token-value" \
  --set ingress.enabled=true \
  --set ingress.className=nginx \
  --set ingress.hosts[0].host=mcp.example.com \
  --set ingress.hosts[0].paths[0].path=/ \
  --set ingress.hosts[0].paths[0].pathType=Prefix
```

## Uninstalling the Chart

To uninstall/delete the `my-mcp` deployment:

```bash
helm uninstall my-mcp
```

This command removes all the Kubernetes components associated with the chart and deletes the release.

## Configuration

The following table lists the configurable parameters of the cryostat-k8s-multi-mcp chart and their default values.

### Global Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `namespace` | Namespace where the chart will be installed | `cryostat-mcp-system` |
| `createNamespace` | Whether to create the namespace if it doesn't exist | `true` |
| `replicaCount` | Number of replicas | `1` |

### Authentication Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `auth.authorizationHeader` | Authorization header value (creates secret if provided) | `""` |
| `auth.existingSecret` | Name of existing secret to use | `""` |
| `auth.secretKey` | Key name in the secret | `"authorization-header"` |

**Note:** Either `auth.authorizationHeader` or `auth.existingSecret` must be provided, but not both.

### Image Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `image.repository` | Container image repository | `quay.io/cryostat/k8s-multi-mcp` |
| `image.tag` | Image tag (defaults to chart appVersion) | `latest` |
| `image.pullPolicy` | Image pull policy | `Always` |

### Service Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `service.type` | Service type | `ClusterIP` |
| `service.port` | Service port | `8080` |
| `service.annotations` | Service annotations | `{}` |

### Resource Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `resources.requests.memory` | Memory request | `256Mi` |
| `resources.requests.cpu` | CPU request | `100m` |
| `resources.limits.memory` | Memory limit | `512Mi` |
| `resources.limits.cpu` | CPU limit | `500m` |

### Environment Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `env.logLevel` | Application log level | `INFO` |
| `env.k8sLogLevel` | k8s-multi-mcp specific log level | `DEBUG` |
| `env.trustCerts` | Trust cluster certificates | `true` |
| `env.trustAllTls` | Trust all TLS certificates | `true` |

### OpenShift Route Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `route.enabled` | Enable OpenShift Route | `false` |
| `route.host` | Custom hostname (leave empty for auto-generated) | `""` |
| `route.tls.enabled` | Enable TLS termination | `true` |
| `route.tls.termination` | TLS termination type (edge/passthrough/reencrypt) | `edge` |
| `route.tls.insecureEdgeTerminationPolicy` | Policy for insecure traffic | `Redirect` |
| `route.annotations` | Route annotations | `{}` |

### Kubernetes Ingress Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `ingress.enabled` | Enable Kubernetes Ingress | `false` |
| `ingress.className` | Ingress class name | `""` |
| `ingress.annotations` | Ingress annotations | `{}` |
| `ingress.hosts` | Ingress hosts configuration | See values.yaml |
| `ingress.tls` | Ingress TLS configuration | `[]` |

### Security Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `podSecurityContext` | Pod security context | See values.yaml |
| `securityContext` | Container security context | See values.yaml |
| `podAnnotations` | Pod annotations | See values.yaml |

### Advanced Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `nodeSelector` | Node selector for pod assignment | `{}` |
| `tolerations` | Tolerations for pod assignment | `[]` |
| `affinity` | Affinity for pod assignment | `{}` |

## Examples

### Example 1: Development Setup

Quick setup for development with minimal configuration:

```bash
helm install dev-mcp ./k8s-multi-mcp/chart/ \
  --set auth.authorizationHeader="Bearer dev-token"
```

### Example 2: Production Setup with Existing Secret

Production setup using an externally managed secret:

```bash
# Create secret using your secret management tool
kubectl create secret generic prod-mcp-secret \
  --from-literal=authorization-header="Bearer $(cat /path/to/token)" \
  -n production

# Install chart
helm install prod-mcp ./k8s-multi-mcp/chart/ \
  --set namespace=production \
  --set createNamespace=false \
  --set auth.existingSecret=prod-mcp-secret \
  --set image.tag=v1.0.0 \
  --set resources.requests.memory=512Mi \
  --set resources.limits.memory=1Gi
```

### Example 3: OpenShift with Custom Route

OpenShift deployment with custom route and TLS:

```bash
helm install ocp-mcp ./k8s-multi-mcp/chart/ \
  --set auth.authorizationHeader="Bearer ocp-token" \
  --set route.enabled=true \
  --set route.host=mcp.apps.ocp.example.com \
  --set route.tls.termination=edge \
  --set route.tls.insecureEdgeTerminationPolicy=Redirect
```

### Example 4: Kubernetes with Ingress and TLS

Kubernetes deployment with nginx ingress and cert-manager:

```bash
helm install k8s-mcp ./k8s-multi-mcp/chart/ \
  --set auth.authorizationHeader="Bearer k8s-token" \
  --set ingress.enabled=true \
  --set ingress.className=nginx \
  --set ingress.annotations."cert-manager\.io/cluster-issuer"=letsencrypt-prod \
  --set ingress.hosts[0].host=mcp.example.com \
  --set ingress.hosts[0].paths[0].path=/ \
  --set ingress.hosts[0].paths[0].pathType=Prefix \
  --set ingress.tls[0].secretName=mcp-tls \
  --set ingress.tls[0].hosts[0]=mcp.example.com
```

### Example 5: Using values.yaml File

Create a custom `my-values.yaml`:

```yaml
namespace: my-namespace
auth:
  existingSecret: my-secret
image:
  tag: v1.0.0
resources:
  requests:
    memory: 512Mi
    cpu: 200m
  limits:
    memory: 1Gi
    cpu: 1000m
route:
  enabled: true
  host: mcp.apps.my-cluster.com
```

Install with custom values:

```bash
helm install my-mcp ./k8s-multi-mcp/chart/ -f my-values.yaml
```

## Upgrading

### Upgrade with New Values

```bash
helm upgrade my-mcp ./k8s-multi-mcp/chart/ \
  --set auth.authorizationHeader="Bearer new-token"
```

### Upgrade Reusing Existing Values

```bash
helm upgrade my-mcp ./k8s-multi-mcp/chart/ \
  --reuse-values \
  --set route.enabled=true
```

## Troubleshooting

### Check Deployment Status

```bash
kubectl get deployment -n cryostat-mcp-system
kubectl get pods -n cryostat-mcp-system
```

### View Logs

```bash
kubectl logs -n cryostat-mcp-system -l app.kubernetes.io/name=cryostat-k8s-multi-mcp -f
```

### Check Secret

```bash
kubectl get secret -n cryostat-mcp-system
kubectl describe secret <secret-name> -n cryostat-mcp-system
```

### Verify RBAC

```bash
kubectl get clusterrole k8s-multi-mcp
kubectl get clusterrolebinding k8s-multi-mcp
```

### Common Issues

1. **Pod not starting**: Check if the secret exists and contains the correct key
2. **Cannot discover Cryostat instances**: Verify RBAC permissions and Cryostat Operator is installed
3. **Route/Ingress not working**: Check annotations and TLS configuration

## Security Considerations

### Secret Management

For production environments, it's recommended to:

1. Use `auth.existingSecret` instead of `auth.authorizationHeader`
2. Manage secrets using external tools (Sealed Secrets, External Secrets Operator, Vault, etc.)
3. Rotate secrets regularly
4. Use RBAC to restrict access to secrets

### Network Policies

Consider adding NetworkPolicies to restrict traffic:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: k8s-multi-mcp-netpol
spec:
  podSelector:
    matchLabels:
      app.kubernetes.io/name: k8s-multi-mcp
  policyTypes:
    - Ingress
    - Egress
  ingress:
    - from:
        - namespaceSelector: {}
      ports:
        - protocol: TCP
          port: 8080
  egress:
    - to:
        - namespaceSelector: {}
```

## Support

For issues and questions:

- GitHub Issues: https://github.com/cryostatio/cryostat-mcp/issues
- Documentation: https://github.com/cryostatio/cryostat-mcp
- Cryostat: https://cryostat.io

## License

This chart is licensed under the same license as the Cryostat project.