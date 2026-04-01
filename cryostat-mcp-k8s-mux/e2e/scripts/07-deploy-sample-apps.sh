#!/usr/bin/env bash
set -euo pipefail

# Script: 07-deploy-sample-apps.sh
# Purpose: Deploy sample applications to apps1 and apps2 projects

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="$(dirname "$SCRIPT_DIR")"
MANIFESTS_DIR="${E2E_DIR}/manifests"

echo "=== Deploying sample applications ==="

# Verify we're connected to OpenShift
if ! oc cluster-info &> /dev/null; then
    echo "✗ Error: Not connected to OpenShift cluster"
    echo "Run 01-verify-crc.sh first"
    exit 1
fi

# Verify projects exist
for PROJECT in apps1 apps2; do
    if ! oc get project "$PROJECT" &> /dev/null; then
        echo "✗ Error: Project '${PROJECT}' does not exist"
        echo "Run 04-create-namespaces.sh first"
        exit 1
    fi
done

# Deploy Quarkus app to apps1
echo "Deploying Quarkus application to apps1 project..."
QUARKUS_MANIFEST="${MANIFESTS_DIR}/sample-app-apps1.yaml"

if [[ ! -f "$QUARKUS_MANIFEST" ]]; then
    echo "✗ Error: Manifest not found at ${QUARKUS_MANIFEST}"
    exit 1
fi

oc apply -f "$QUARKUS_MANIFEST"
echo "✓ Quarkus app manifest applied"

# Deploy WildFly app to apps2
echo ""
echo "Deploying WildFly application to apps2 project..."
WILDFLY_MANIFEST="${MANIFESTS_DIR}/sample-app-apps2.yaml"

if [[ ! -f "$WILDFLY_MANIFEST" ]]; then
    echo "✗ Error: Manifest not found at ${WILDFLY_MANIFEST}"
    exit 1
fi

oc apply -f "$WILDFLY_MANIFEST"
echo "✓ WildFly app manifest applied"

# Wait for deployments to be ready
echo ""
echo "Waiting for Quarkus app to be ready..."
oc wait --for=condition=Available deployment/quarkus-cryostat-agent \
    -n apps1 --timeout=300s

echo ""
echo "Waiting for WildFly app to be ready..."
oc wait --for=condition=Available deployment/wildfly-cryostat-agent \
    -n apps2 --timeout=300s

echo ""
echo "✓ All sample applications deployed and ready"
echo ""
echo "Apps1 project (Quarkus):"
oc get all -n apps1

echo ""
echo "Apps2 project (WildFly):"
oc get all -n apps2

echo ""
echo "Sample applications are now running and should be discoverable by their respective Cryostat instances"