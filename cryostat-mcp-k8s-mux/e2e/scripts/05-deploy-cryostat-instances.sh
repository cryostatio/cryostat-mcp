#!/usr/bin/env bash
set -euo pipefail

# Script: 05-deploy-cryostat-instances.sh
# Purpose: Deploy Cryostat CR instances c1 and c2

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="$(dirname "$SCRIPT_DIR")"
MANIFESTS_DIR="${E2E_DIR}/manifests"

echo "=== Deploying Cryostat instances ==="

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

# Verify projects exist
for PROJECT in c1 c2; do
    if ! oc get project "$PROJECT" &> /dev/null; then
        echo "✗ Error: Project '${PROJECT}' does not exist"
        echo "Run 04-create-namespaces.sh first"
        exit 1
    fi
done

echo ""
echo "Deploying Cryostat c1 instance..."
if oc get cryostat -n c1 cryostat-c1 &> /dev/null; then
    echo "  ✓ Cryostat c1 already exists, updating..."
    oc apply -f "${MANIFESTS_DIR}/cryostat-c1.yaml"
else
    echo "  → Creating Cryostat c1..."
    oc apply -f "${MANIFESTS_DIR}/cryostat-c1.yaml"
fi

echo ""
echo "Deploying Cryostat c2 instance..."
if oc get cryostat -n c2 cryostat-c2 &> /dev/null; then
    echo "  ✓ Cryostat c2 already exists, updating..."
    oc apply -f "${MANIFESTS_DIR}/cryostat-c2.yaml"
else
    echo "  → Creating Cryostat c2..."
    oc apply -f "${MANIFESTS_DIR}/cryostat-c2.yaml"
fi

echo ""
echo "Waiting for Cryostat instances to be ready..."
echo "This may take several minutes as images are pulled and pods are created..."
echo ""

# Function to wait for Cryostat instance
wait_for_cryostat() {
    local namespace=$1
    local name=$2
    local timeout=600  # 10 minutes
    local elapsed=0
    
    echo "Waiting for Cryostat ${name} in namespace ${namespace}..."
    
    while [ $elapsed -lt $timeout ]; do
        # Check if the Cryostat CR has an applicationUrl in status
        APP_URL=$(oc get cryostat "$name" -n "$namespace" -o jsonpath='{.status.applicationUrl}' 2>/dev/null || echo "")
        
        # Also check if all pods are ready
        PODS_READY=true
        if oc get pods -n "$namespace" -l app.kubernetes.io/name=cryostat &> /dev/null; then
            # Check if all pods are in Running state and all containers are ready
            NOT_READY=$(oc get pods -n "$namespace" -l app.kubernetes.io/name=cryostat -o jsonpath='{range .items[*]}{.metadata.name}{" "}{.status.phase}{" "}{.status.containerStatuses[*].ready}{"\n"}{end}' 2>/dev/null | grep -v "Running true true true true true" || echo "")
            if [ -n "$NOT_READY" ]; then
                PODS_READY=false
            fi
        else
            PODS_READY=false
        fi
        
        if [ -n "$APP_URL" ] && [ "$PODS_READY" = true ]; then
            echo "  ✓ Cryostat ${name} is ready"
            echo "    Application URL: ${APP_URL}"
            echo "    All pods are running and ready"
            return 0
        fi
        
        # Show current status
        PHASE=$(oc get cryostat "$name" -n "$namespace" -o jsonpath='{.status.phase}' 2>/dev/null || echo "Unknown")
        POD_STATUS=$(oc get pods -n "$namespace" -l app.kubernetes.io/name=cryostat --no-headers 2>/dev/null | awk '{print $2}' | tr '\n' ' ' || echo "No pods")
        echo "  Status: ${PHASE}, Pods: ${POD_STATUS}(elapsed: ${elapsed}s)"
        
        sleep 10
        elapsed=$((elapsed + 10))
    done
    
    echo "  ✗ Timeout waiting for Cryostat ${name}"
    echo ""
    echo "Current status:"
    oc get cryostat "$name" -n "$namespace" -o yaml
    echo ""
    echo "Pod status:"
    oc get pods -n "$namespace" -l app.kubernetes.io/name=cryostat
    echo ""
    echo "Pod details:"
    oc describe pods -n "$namespace" -l app.kubernetes.io/name=cryostat
    return 1
}

# Wait for both instances
wait_for_cryostat "c1" "cryostat-c1"
echo ""
wait_for_cryostat "c2" "cryostat-c2"

echo ""
echo "Verifying Routes are created..."

# Check c1 route
if oc get route -n c1 cryostat-c1 &> /dev/null; then
    C1_ROUTE=$(oc get route -n c1 cryostat-c1 -o jsonpath='{.spec.host}')
    echo "  ✓ Cryostat c1 Route: https://${C1_ROUTE}"
else
    echo "  ✗ Warning: Cryostat c1 Route not found"
fi

# Check c2 route
if oc get route -n c2 cryostat-c2 &> /dev/null; then
    C2_ROUTE=$(oc get route -n c2 cryostat-c2 -o jsonpath='{.spec.host}')
    echo "  ✓ Cryostat c2 Route: https://${C2_ROUTE}"
else
    echo "  ✗ Warning: Cryostat c2 Route not found"
fi

echo ""
echo "✓ Cryostat instances deployed successfully"
echo ""
echo "Instance details:"
echo ""
echo "Cryostat c1 (namespace: c1):"
oc get all -n c1 -l app.kubernetes.io/name=cryostat
echo ""
echo "Cryostat c2 (namespace: c2):"
oc get all -n c2 -l app.kubernetes.io/name=cryostat

echo ""
echo "Access credentials: user:pass"
