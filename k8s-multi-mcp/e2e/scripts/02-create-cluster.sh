#!/usr/bin/env bash
set -euo pipefail

# Script: 02-create-cluster.sh
# Purpose: Create a kind cluster for e2e testing

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="$(dirname "$SCRIPT_DIR")"
MANIFESTS_DIR="${E2E_DIR}/manifests"

CLUSTER_NAME="cryostat-mcp-e2e"
KIND_CONFIG="${MANIFESTS_DIR}/kind-config.yaml"

echo "=== Creating kind cluster ==="

# Check if cluster already exists
if kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
    echo "✓ Cluster '${CLUSTER_NAME}' already exists"
    echo ""
    echo "To recreate the cluster, first run:"
    echo "  kind delete cluster --name ${CLUSTER_NAME}"
    exit 0
fi

# Verify kind config exists
if [[ ! -f "$KIND_CONFIG" ]]; then
    echo "Error: kind config not found at ${KIND_CONFIG}"
    exit 1
fi

echo "Creating cluster '${CLUSTER_NAME}' with config from ${KIND_CONFIG}..."
kind create cluster --config "$KIND_CONFIG"

echo ""
echo "Setting kubectl context..."
kubectl config use-context "kind-${CLUSTER_NAME}"

echo ""
echo "Waiting for cluster to be ready..."
kubectl wait --for=condition=Ready nodes --all --timeout=120s

echo ""
echo "✓ Cluster created successfully"
echo ""
echo "Cluster info:"
kubectl cluster-info --context "kind-${CLUSTER_NAME}"

echo ""
echo "Nodes:"
kubectl get nodes

echo ""
echo "To interact with the cluster, use:"
echo "  kubectl --context kind-${CLUSTER_NAME} <command>"
echo ""
echo "Or set as default context:"
echo "  kubectl config use-context kind-${CLUSTER_NAME}"