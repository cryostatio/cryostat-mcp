#!/usr/bin/env bash
set -euo pipefail

# Script: 03-install-cryostat-operator.sh
# Purpose: Install Cryostat Operator using operator-sdk run bundle
# Based on cryostat-operator Makefile deploy_bundle target

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="$(dirname "$SCRIPT_DIR")"

BUNDLE_VERSION="4.2.0-dev-ocp"
BUNDLE_IMG="quay.io/cryostat/cryostat-operator-bundle:${BUNDLE_VERSION}"
OPERATOR_NAME="cryostat-operator"

echo "=== Installing Cryostat Operator ==="

# Verify we're connected to OpenShift
if ! oc cluster-info &> /dev/null; then
    echo "✗ Error: Not connected to OpenShift cluster"
    echo "Run 01-verify-crc.sh first"
    exit 1
fi

# Verify cert-manager is installed
if ! oc get deployment -n cert-manager cert-manager &> /dev/null; then
    echo "✗ Error: cert-manager is not installed"
    echo "Run 02-install-cert-manager.sh first"
    exit 1
fi

# Check if operator-sdk is available
if ! command -v operator-sdk &> /dev/null; then
    echo "✗ Error: operator-sdk is not installed"
    echo ""
    echo "Install operator-sdk:"
    echo "  https://sdk.operatorframework.io/docs/installation/"
    exit 1
fi

# Check if Cryostat operator is already installed
CSV_INFO=$(oc get csv -A -o json 2>/dev/null | jq -r '.items[] | select(.metadata.name | contains("cryostat-operator")) | "\(.metadata.name) \(.metadata.namespace) \(.status.phase)"' | head -n 1)

if [ -n "$CSV_INFO" ]; then
    CSV_NAME=$(echo "$CSV_INFO" | awk '{print $1}')
    CSV_NAMESPACE=$(echo "$CSV_INFO" | awk '{print $2}')
    CSV_PHASE=$(echo "$CSV_INFO" | awk '{print $3}')
    
    echo "✓ Cryostat operator is already installed"
    echo ""
    echo "Installed version:"
    echo "  CSV: $CSV_NAME"
    echo "  Namespace: $CSV_NAMESPACE"
    echo "  Phase: $CSV_PHASE"
    
    if [ "$CSV_PHASE" = "Succeeded" ]; then
        echo ""
        echo "Operator is ready and healthy"
        echo ""
        echo "Installed components:"
        oc get all -n "$CSV_NAMESPACE" -l app.kubernetes.io/name=cryostat-operator 2>/dev/null || echo "  (No resources found with label app.kubernetes.io/name=cryostat-operator)"
        exit 0
    else
        echo ""
        echo "⚠ Warning: Operator CSV exists but is not in Succeeded phase"
        echo "Current phase: $CSV_PHASE"
        echo ""
        echo "To reinstall, first run cleanup.sh"
        exit 1
    fi
fi

# Check for orphaned OLM resources (CatalogSource, Subscription, etc. without CSV)
# This can happen if a previous installation was partially cleaned up
CATALOG_SOURCE=$(oc get catalogsource -n cryostat-operator-system cryostat-operator-catalog 2>/dev/null || echo "")
SUBSCRIPTION=$(oc get subscription -n cryostat-operator-system -o name 2>/dev/null | grep cryostat-operator || echo "")

if [ -n "$CATALOG_SOURCE" ] || [ -n "$SUBSCRIPTION" ]; then
    echo "⚠ Found orphaned OLM resources from previous installation"
    echo "Cleaning up before proceeding..."
    
    # Delete subscription first
    if [ -n "$SUBSCRIPTION" ]; then
        echo "  Deleting subscription..."
        oc delete subscription -n cryostat-operator-system --all --ignore-not-found=true
    fi
    
    # Delete catalog source
    if [ -n "$CATALOG_SOURCE" ]; then
        echo "  Deleting catalog source..."
        oc delete catalogsource -n cryostat-operator-system cryostat-operator-catalog --ignore-not-found=true
    fi
    
    # Delete operator group if it exists
    echo "  Deleting operator group..."
    oc delete operatorgroup -n cryostat-operator-system --all --ignore-not-found=true
    
    # Wait a moment for resources to be deleted
    echo "  Waiting for cleanup to complete..."
    sleep 5
    
    echo "✓ Cleanup complete"
fi

echo ""
echo "Installing Cryostat Operator bundle: ${BUNDLE_IMG}"
echo "This may take several minutes..."
echo ""

# Create namespace for operator-sdk (it expects this to exist)
OPERATOR_NAMESPACE="cryostat-operator-system"
echo "Creating namespace: ${OPERATOR_NAMESPACE}"
oc create namespace "${OPERATOR_NAMESPACE}" --dry-run=client -o yaml | oc apply -f -
oc project "${OPERATOR_NAMESPACE}"

# Use operator-sdk to install the bundle
# --install-mode AllNamespaces allows the operator to watch all namespaces
if ! operator-sdk run bundle --install-mode AllNamespaces "${BUNDLE_IMG}"; then
    echo ""
    echo "✗ Error: Failed to install operator bundle"
    echo ""
    echo "Troubleshooting:"
    echo "1. Check if the bundle image is accessible:"
    echo "   podman pull ${BUNDLE_IMG}"
    echo "2. Check OLM status:"
    echo "   oc get csv -A"
    echo "   oc get subscription -A"
    echo "3. Check operator-sdk version:"
    echo "   operator-sdk version"
    exit 1
fi

echo ""
echo "Waiting for operator to be ready..."

# Wait for CSV to be in Succeeded phase
TIMEOUT=120  # 120 iterations * 10 seconds = 20 minutes
for i in $(seq 1 $TIMEOUT); do
    CSV_NAME=$(oc get csv -A -o name 2>/dev/null | grep "cryostat-operator" | head -n 1 || echo "")
    
    if [ -n "$CSV_NAME" ]; then
        NAMESPACE=$(oc get "$CSV_NAME" -A -o jsonpath='{.items[0].metadata.namespace}' 2>/dev/null || echo "")
        PHASE=$(oc get "$CSV_NAME" -n "$NAMESPACE" -o jsonpath='{.status.phase}' 2>/dev/null || echo "Unknown")
        
        if [ "$PHASE" = "Succeeded" ]; then
            echo "✓ Cryostat operator is ready"
            break
        fi
        
        if [ $i -eq $TIMEOUT ]; then
            echo "✗ Error: Timeout waiting for operator to be ready (waited $((TIMEOUT * 10)) seconds)"
            echo "Current phase: $PHASE"
            echo ""
            echo "Check CSV status:"
            oc describe "$CSV_NAME" -n "$NAMESPACE"
            exit 1
        fi
        
        echo "  Phase: $PHASE in namespace: $NAMESPACE (attempt $i/$TIMEOUT)"
    else
        echo "  Waiting for CSV... (attempt $i/$TIMEOUT)"
    fi
    
    sleep 10
done

echo ""
echo "✓ Cryostat operator installation complete"
echo ""
echo "Installed components:"
CSV_INFO=$(oc get csv -A -o json 2>/dev/null | jq -r '.items[] | select(.metadata.name | contains("cryostat-operator")) | "\(.metadata.name) \(.metadata.namespace)"' | head -n 1)
if [ -n "$CSV_INFO" ]; then
    CSV_NAME=$(echo "$CSV_INFO" | awk '{print $1}')
    NAMESPACE=$(echo "$CSV_INFO" | awk '{print $2}')
    echo "  CSV: $CSV_NAME"
    echo "  Namespace: $NAMESPACE"
    echo ""
    oc get all -n "$NAMESPACE" -l app.kubernetes.io/name=cryostat-operator 2>/dev/null || echo "  (No resources found with label app.kubernetes.io/name=cryostat-operator)"
else
    echo "  Warning: Could not find CSV information"
fi
