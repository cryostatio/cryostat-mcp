#!/usr/bin/env bash
set -euo pipefail

# Script: 08-deploy-sample-apps.sh
# Purpose: Deploy sample applications to apps1 and apps2 namespaces

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="$(dirname "$SCRIPT_DIR")"
MANIFESTS_DIR="${E2E_DIR}/manifests"

CLUSTER_NAME="cryostat-mcp-e2e"

echo "=== Deploying sample applications ==="

# Check if cluster exists
if ! kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
    echo "Error: Cluster '${CLUSTER_NAME}' does not exist"
    echo "Run 02-create-cluster.sh first"
    exit 1
fi

# Set context
kubectl config use-context "kind-${CLUSTER_NAME}"

# Deploy Quarkus app to apps1
echo "Deploying Quarkus application to apps1 namespace..."
QUARKUS_MANIFEST="${MANIFESTS_DIR}/sample-app-apps1.yaml"

if [[ ! -f "$QUARKUS_MANIFEST" ]]; then
    echo "Error: Manifest not found at ${QUARKUS_MANIFEST}"
    exit 1
fi

kubectl apply -f "$QUARKUS_MANIFEST"
echo "✓ Quarkus app manifest applied"

# Deploy WildFly app to apps2
echo ""
echo "Deploying WildFly application to apps2 namespace..."
WILDFLY_MANIFEST="${MANIFESTS_DIR}/sample-app-apps2.yaml"

if [[ ! -f "$WILDFLY_MANIFEST" ]]; then
    echo "Error: Manifest not found at ${WILDFLY_MANIFEST}"
    exit 1
fi

kubectl apply -f "$WILDFLY_MANIFEST"
echo "✓ WildFly app manifest applied"

# Wait for deployments to be ready
echo ""
echo "Waiting for Quarkus app to be ready..."
kubectl wait --for=condition=Available deployment/quarkus-cryostat-agent \
    -n apps1 --timeout=300s

echo ""
echo "Waiting for WildFly app to be ready..."
kubectl wait --for=condition=Available deployment/wildfly-cryostat-agent \
    -n apps2 --timeout=300s

echo ""
echo "✓ All sample applications deployed and ready"
echo ""
echo "Apps1 namespace (Quarkus):"
kubectl get all -n apps1

echo ""
echo "Apps2 namespace (WildFly):"
kubectl get all -n apps2

echo ""
echo "Sample applications are now running and should be discoverable by their respective Cryostat instances"