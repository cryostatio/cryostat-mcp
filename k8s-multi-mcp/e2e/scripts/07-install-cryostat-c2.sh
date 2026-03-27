#!/usr/bin/env bash
set -euo pipefail

# Script: 07-install-cryostat-c2.sh
# Purpose: Install Cryostat instance c2 using Helm

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="$(dirname "$SCRIPT_DIR")"
HELM_VALUES_DIR="${E2E_DIR}/helm-values"

CLUSTER_NAME="cryostat-mcp-e2e"
RELEASE_NAME="cryostat-c2"
NAMESPACE="c2"
CHART="cryostat/cryostat"
VALUES_FILE="${HELM_VALUES_DIR}/cryostat-c2-values.yaml"

echo "=== Installing Cryostat instance c2 ==="

# Check if cluster exists
if ! kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
    echo "Error: Cluster '${CLUSTER_NAME}' does not exist"
    echo "Run 02-create-cluster.sh first"
    exit 1
fi

# Set context
kubectl config use-context "kind-${CLUSTER_NAME}"

# Verify namespace exists
if ! kubectl get namespace "$NAMESPACE" &> /dev/null; then
    echo "Error: Namespace '${NAMESPACE}' does not exist"
    echo "Run 04-create-namespaces.sh first"
    exit 1
fi

# Verify values file exists
if [[ ! -f "$VALUES_FILE" ]]; then
    echo "Error: Values file not found at ${VALUES_FILE}"
    exit 1
fi

echo "Creating required secrets..."

# Create htpasswd secret for basic authentication
# Using bcrypt hash for password "pass"
# Create a temporary file with the htpasswd content
HTPASSWD_FILE=$(mktemp)
echo 'user:$2y$05$xUMuOEzt/C83Cxfz99/OXug.IJlt4IcRXeeWp.gl4G6ZhKfc1x/Ja' > "$HTPASSWD_FILE"

kubectl create secret generic cryostat-c2-htpasswd \
    --from-file=htpasswd="$HTPASSWD_FILE" \
    -n "$NAMESPACE" \
    --dry-run=client -o yaml | kubectl apply -f -

rm -f "$HTPASSWD_FILE"

# Create cookie secret for oauth2-proxy
kubectl create secret generic cryostat-c2-cookie-secret \
    --from-literal=COOKIE_SECRET='9UFyQsyhXKXd_16fzpSHK_VdQqLCeyOLgiLpQPljdcU' \
    -n "$NAMESPACE" \
    --dry-run=client -o yaml | kubectl apply -f -

echo "✓ Secrets created"
echo ""

echo "Installing Cryostat in namespace '${NAMESPACE}'..."
echo "Using values from: ${VALUES_FILE}"
echo ""

# Check if release already exists
if helm list -n "$NAMESPACE" | grep -q "^${RELEASE_NAME}[[:space:]]"; then
    echo "Release '${RELEASE_NAME}' already exists in namespace '${NAMESPACE}'"
    echo "Upgrading..."
    helm upgrade "$RELEASE_NAME" "$CHART" \
        --namespace "$NAMESPACE" \
        --values "$VALUES_FILE" \
        --wait=false
    echo "✓ Helm upgrade initiated"
else
    echo "Installing new release..."
    helm install "$RELEASE_NAME" "$CHART" \
        --namespace "$NAMESPACE" \
        --values "$VALUES_FILE" \
        --wait=false
    echo "✓ Helm installation initiated"
fi

echo ""
echo "Waiting for deployment to be created..."
# Wait for the deployment to exist
for i in {1..30}; do
    if kubectl get deployment -n "$NAMESPACE" cryostat-c2-v4 &>/dev/null; then
        echo "✓ Deployment created"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "Error: Deployment not created after 30 seconds"
        exit 1
    fi
    sleep 1
done

echo "Patching deployment to fix htpasswd secret permissions..."
# Find the index of the htpasswd volume in the volumes array
VOLUME_INDEX=$(kubectl get deployment -n "$NAMESPACE" cryostat-c2-v4 -o json | jq '.spec.template.spec.volumes | map(.name) | index("cryostat-c2-htpasswd")')

if [[ "$VOLUME_INDEX" == "null" ]]; then
    echo "Error: Could not find cryostat-c2-htpasswd volume in deployment"
    exit 1
fi

echo "Found htpasswd volume at index $VOLUME_INDEX"

# Use JSON patch to directly set the defaultMode value
kubectl patch deployment -n "$NAMESPACE" cryostat-c2-v4 --type=json -p="[{\"op\": \"replace\", \"path\": \"/spec/template/spec/volumes/$VOLUME_INDEX/secret/defaultMode\", \"value\": 292}]"

echo "Waiting for deployment rollout..."
kubectl rollout status deployment -n "$NAMESPACE" cryostat-c2-v4 --timeout=300s

echo ""
echo "Waiting for Cryostat pods to be ready..."
kubectl wait --for=condition=Ready pods -l app.kubernetes.io/name=cryostat \
    -n "$NAMESPACE" --timeout=300s

echo ""
echo "✓ Cryostat c2 is ready"
echo ""
echo "Deployment status:"
kubectl get all -n "$NAMESPACE"

echo ""
echo "Access Cryostat c2 at: http://localhost:8081"