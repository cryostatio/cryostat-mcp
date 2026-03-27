#!/usr/bin/env bash
set -euo pipefail

# Script: 04-create-namespaces.sh
# Purpose: Create all required namespaces for the e2e test

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="$(dirname "$SCRIPT_DIR")"

CLUSTER_NAME="cryostat-mcp-e2e"

echo "=== Creating namespaces ==="

# Check if cluster exists
if ! kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
    echo "Error: Cluster '${CLUSTER_NAME}' does not exist"
    echo "Run 02-create-cluster.sh first"
    exit 1
fi

# Set context
kubectl config use-context "kind-${CLUSTER_NAME}"

# List of namespaces to create
NAMESPACES=(
    "cryostat-multi-mcp"  # k8s-multi-mcp deployment
    "c1"                   # Cryostat instance 1
    "apps1"                # Applications monitored by c1
    "c2"                   # Cryostat instance 2
    "apps2"                # Applications monitored by c2
)

echo "Creating namespaces..."
echo ""

for NS in "${NAMESPACES[@]}"; do
    if kubectl get namespace "$NS" &> /dev/null; then
        echo "  ✓ Namespace '${NS}' already exists"
    else
        echo "  → Creating namespace '${NS}'..."
        kubectl create namespace "$NS"
        echo "  ✓ Created successfully"
    fi
done

echo ""
echo "✓ All namespaces created"
echo ""
echo "Namespace list:"
kubectl get namespaces | grep -E "(NAME|cryostat-multi-mcp|^c1|^c2|apps1|apps2)"