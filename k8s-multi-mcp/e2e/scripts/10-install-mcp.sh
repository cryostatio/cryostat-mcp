#!/usr/bin/env bash
set -euo pipefail

# Script: 10-install-mcp.sh
# Purpose: Install k8s-multi-mcp using Helm

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="$(dirname "$SCRIPT_DIR")"
PROJECT_ROOT="$(dirname "$E2E_DIR")"
HELM_VALUES_DIR="${E2E_DIR}/helm-values"
CHART_DIR="${PROJECT_ROOT}/chart"

CLUSTER_NAME="cryostat-mcp-e2e"
RELEASE_NAME="k8s-multi-mcp"
NAMESPACE="cryostat-multi-mcp"
VALUES_FILE="${HELM_VALUES_DIR}/k8s-multi-mcp-values.yaml"

echo "=== Installing k8s-multi-mcp ==="

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

# Verify chart directory exists
if [[ ! -d "$CHART_DIR" ]]; then
    echo "Error: Chart directory not found at ${CHART_DIR}"
    exit 1
fi

# Verify values file exists
if [[ ! -f "$VALUES_FILE" ]]; then
    echo "Error: Values file not found at ${VALUES_FILE}"
    exit 1
fi

echo "Installing k8s-multi-mcp in namespace '${NAMESPACE}'..."
echo "Using chart from: ${CHART_DIR}"
echo "Using values from: ${VALUES_FILE}"
echo ""

# Check if release already exists
if helm list -n "$NAMESPACE" | grep -q "^${RELEASE_NAME}[[:space:]]"; then
    echo "Release '${RELEASE_NAME}' already exists in namespace '${NAMESPACE}'"
    echo "Upgrading..."
    helm upgrade "$RELEASE_NAME" "$CHART_DIR" \
        --namespace "$NAMESPACE" \
        --values "$VALUES_FILE" \
        --wait \
        --timeout 5m
    echo "✓ Upgrade complete"
else
    echo "Installing new release..."
    helm install "$RELEASE_NAME" "$CHART_DIR" \
        --namespace "$NAMESPACE" \
        --values "$VALUES_FILE" \
        --wait \
        --timeout 5m
    echo "✓ Installation complete"
fi

echo ""
echo "✓ k8s-multi-mcp is ready"
echo ""
echo "Deployment status:"
kubectl get all -n "$NAMESPACE"

echo ""
echo "Access k8s-multi-mcp at: http://localhost:30082"
echo ""
echo "To view logs:"
echo "  kubectl logs -n ${NAMESPACE} -l app.kubernetes.io/name=cryostat-k8s-multi-mcp -f"