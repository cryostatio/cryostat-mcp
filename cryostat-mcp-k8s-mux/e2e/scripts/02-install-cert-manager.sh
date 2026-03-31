#!/usr/bin/env bash
set -euo pipefail

# Script: 02-install-cert-manager.sh
# Purpose: Install cert-manager using manifest (prerequisite for Cryostat Operator)
# Based on cryostat-operator Makefile approach

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="$(dirname "$SCRIPT_DIR")"

CERT_MANAGER_VERSION="1.12.14"
CERT_MANAGER_MANIFEST="https://github.com/cert-manager/cert-manager/releases/download/v${CERT_MANAGER_VERSION}/cert-manager.yaml"

echo "=== Installing cert-manager ==="

# Verify we're connected to OpenShift
if ! oc cluster-info &> /dev/null; then
    echo "✗ Error: Not connected to OpenShift cluster"
    echo "Run 01-verify-crc.sh first"
    exit 1
fi

# Check if cert-manager is already installed
if oc get namespace cert-manager &> /dev/null; then
    echo "✓ cert-manager namespace already exists"
    
    # Check if deployments are ready
    if oc get deployment -n cert-manager cert-manager &> /dev/null; then
        echo "✓ cert-manager is already installed"
        echo ""
        echo "Installed components:"
        oc get pods -n cert-manager
        exit 0
    fi
fi

echo "Installing cert-manager version ${CERT_MANAGER_VERSION}..."
echo "Manifest: ${CERT_MANAGER_MANIFEST}"
echo ""

# Apply cert-manager manifest
# Using --validate=false as recommended by cryostat-operator Makefile
# Using 'apply' instead of 'create' to handle re-installation (updates existing cluster resources)
oc apply --validate=false -f "${CERT_MANAGER_MANIFEST}"

if [[ $? -ne 0 ]]; then
    echo "✗ Error: Failed to apply cert-manager manifest"
    exit 1
fi

echo ""
echo "Waiting for cert-manager to be ready..."
echo "This may take a few minutes..."

# Wait for cert-manager namespace
for i in {1..30}; do
    if oc get namespace cert-manager &> /dev/null; then
        echo "✓ cert-manager namespace created"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "✗ Error: Timeout waiting for cert-manager namespace"
        exit 1
    fi
    sleep 2
done

# Wait for cert-manager deployments to be created
echo ""
echo "Waiting for cert-manager deployments..."
for i in {1..60}; do
    if oc get deployment -n cert-manager cert-manager cert-manager-webhook cert-manager-cainjector &> /dev/null 2>&1; then
        echo "✓ All cert-manager deployments created"
        break
    fi
    if [ $i -eq 60 ]; then
        echo "✗ Error: Timeout waiting for cert-manager deployments"
        echo ""
        echo "Current state:"
        oc get all -n cert-manager
        exit 1
    fi
    sleep 2
done

# Wait for deployments to be available
echo ""
echo "Waiting for cert-manager deployments to be available..."
oc wait --for=condition=Available deployment/cert-manager \
    -n cert-manager --timeout=180s
oc wait --for=condition=Available deployment/cert-manager-webhook \
    -n cert-manager --timeout=180s
oc wait --for=condition=Available deployment/cert-manager-cainjector \
    -n cert-manager --timeout=180s

# Wait for webhook service to be ready
echo ""
echo "Waiting for cert-manager webhook service..."
for i in {1..30}; do
    if oc get service cert-manager-webhook -n cert-manager &> /dev/null; then
        # Check if service has endpoints (pods are ready)
        ENDPOINTS=$(oc get endpoints cert-manager-webhook -n cert-manager -o jsonpath='{.subsets[*].addresses[*].ip}' 2>/dev/null || echo "")
        if [ -n "$ENDPOINTS" ]; then
            echo "✓ cert-manager webhook service is ready"
            break
        fi
    fi
    if [ $i -eq 30 ]; then
        echo "✗ Error: Timeout waiting for cert-manager webhook service"
        echo ""
        echo "Current state:"
        oc get service,endpoints -n cert-manager
        exit 1
    fi
    echo "  Waiting for webhook endpoints... (attempt $i/30)"
    sleep 2
done

echo ""
echo "✓ cert-manager installation complete"
echo ""
echo "Installed components:"
oc get all -n cert-manager

echo ""
echo "cert-manager version: ${CERT_MANAGER_VERSION}"
