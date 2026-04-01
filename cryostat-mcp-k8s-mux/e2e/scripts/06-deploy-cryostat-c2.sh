#!/usr/bin/env bash
set -euo pipefail

# Script: 06-deploy-cryostat-c2.sh
# Purpose: Deploy Cryostat c2 instance using Cryostat Operator CR

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="$(dirname "$SCRIPT_DIR")"
MANIFESTS_DIR="${E2E_DIR}/manifests"

NAMESPACE="c2"
CRYOSTAT_NAME="cryostat-c2"
MANIFEST_FILE="${MANIFESTS_DIR}/cryostat-c2.yaml"

echo "=== Deploying Cryostat c2 instance ==="

# Verify we're connected to OpenShift
if ! oc cluster-info &> /dev/null; then
    echo "✗ Error: Not connected to OpenShift cluster"
    echo "Run 01-verify-crc.sh first"
    exit 1
fi

# Verify Cryostat operator is installed
if ! oc get crd cryostats.operator.cryostat.io &> /dev/null; then
    echo "✗ Error: Cryostat CRD not found"
    echo "Run 03-install-cryostat-operator.sh first"
    exit 1
fi

# Verify namespace exists
if ! oc get project "$NAMESPACE" &> /dev/null; then
    echo "✗ Error: Project '${NAMESPACE}' does not exist"
    echo "Run 04-create-namespaces.sh first"
    exit 1
fi

# Verify manifest file exists
if [[ ! -f "$MANIFEST_FILE" ]]; then
    echo "✗ Error: Manifest file not found at ${MANIFEST_FILE}"
    exit 1
fi

echo ""
echo "Deploying Cryostat c2 instance..."
if oc get cryostat -n "$NAMESPACE" "$CRYOSTAT_NAME" &> /dev/null; then
    echo "  ✓ Cryostat c2 already exists, updating..."
    oc apply -f "$MANIFEST_FILE"
else
    echo "  → Creating Cryostat c2..."
    oc apply -f "$MANIFEST_FILE"
fi

echo ""
echo "Waiting for Cryostat c2 to be ready..."
echo "This may take several minutes as images are pulled and pods are created..."
echo ""

# Wait for Cryostat instance
TIMEOUT=600  # 10 minutes
ELAPSED=0

while [ $ELAPSED -lt $TIMEOUT ]; do
    # Check if the Cryostat CR has an applicationUrl in status
    APP_URL=$(oc get cryostat "$CRYOSTAT_NAME" -n "$NAMESPACE" -o jsonpath='{.status.applicationUrl}' 2>/dev/null || echo "")
    
    # Also check if all pods are ready
    PODS_READY=true
    if oc get pods -n "$NAMESPACE" -l app.kubernetes.io/name=cryostat &> /dev/null; then
        # Check if all pods are in Running state and all containers are ready
        NOT_READY=$(oc get pods -n "$NAMESPACE" -l app.kubernetes.io/name=cryostat -o jsonpath='{range .items[*]}{.metadata.name}{" "}{.status.phase}{" "}{.status.containerStatuses[*].ready}{"\n"}{end}' 2>/dev/null | grep -v "Running true true true true true" || echo "")
        if [ -n "$NOT_READY" ]; then
            PODS_READY=false
        fi
    else
        PODS_READY=false
    fi
    
    if [ -n "$APP_URL" ] && [ "$PODS_READY" = true ]; then
        echo "  ✓ Cryostat c2 is ready"
        echo "    Application URL: ${APP_URL}"
        echo "    All pods are running and ready"
        break
    fi
    
    # Show current status
    PHASE=$(oc get cryostat "$CRYOSTAT_NAME" -n "$NAMESPACE" -o jsonpath='{.status.phase}' 2>/dev/null || echo "Unknown")
    POD_STATUS=$(oc get pods -n "$NAMESPACE" -l app.kubernetes.io/name=cryostat --no-headers 2>/dev/null | awk '{print $2}' | tr '\n' ' ' || echo "No pods")
    echo "  Status: ${PHASE}, Pods: ${POD_STATUS}(elapsed: ${ELAPSED}s)"
    
    sleep 10
    ELAPSED=$((ELAPSED + 10))
done

if [ $ELAPSED -ge $TIMEOUT ]; then
    echo "  ✗ Timeout waiting for Cryostat c2"
    echo ""
    echo "Current status:"
    oc get cryostat "$CRYOSTAT_NAME" -n "$NAMESPACE" -o yaml
    echo ""
    echo "Pod status:"
    oc get pods -n "$NAMESPACE" -l app.kubernetes.io/name=cryostat
    echo ""
    echo "Pod details:"
    oc describe pods -n "$NAMESPACE" -l app.kubernetes.io/name=cryostat
    exit 1
fi

echo ""
echo "Verifying Route is created..."

# Check route
if oc get route -n "$NAMESPACE" "$CRYOSTAT_NAME" &> /dev/null; then
    ROUTE=$(oc get route -n "$NAMESPACE" "$CRYOSTAT_NAME" -o jsonpath='{.spec.host}')
    echo "  ✓ Cryostat c2 Route: https://${ROUTE}"
else
    echo "  ✗ Warning: Cryostat c2 Route not found"
fi

echo ""
echo "✓ Cryostat c2 deployed successfully"
echo ""
echo "Instance details:"
oc get all -n "$NAMESPACE" -l app.kubernetes.io/name=cryostat

echo ""
echo "Access credentials: user:pass" # notsecret
