#!/usr/bin/env bash
set -euo pipefail

# Script: 01-verify-crc.sh
# Purpose: Verify CRC is installed, running, and properly configured

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== Verifying CRC installation and status ==="

# Check if crc command is available
if ! command -v crc &> /dev/null; then
    echo "✗ Error: 'crc' command not found"
    echo ""
    echo "Please install CodeReady Containers (CRC) first:"
    echo "  https://developers.redhat.com/products/openshift-local/overview"
    echo ""
    echo "After installation, set up CRC with:"
    echo "  crc setup"
    echo "  crc start"
    exit 1
fi

CRC_VERSION=$(crc version | grep "CRC version" | awk '{print $3}' || echo "unknown")
echo "✓ CRC is installed (version: $CRC_VERSION)"

# Check if oc command is available
if ! command -v oc &> /dev/null; then
    echo "✗ Error: 'oc' command not found"
    echo ""
    echo "The OpenShift CLI (oc) is required. It should be available after CRC installation."
    echo "You can also download it from: https://mirror.openshift.com/pub/openshift-v4/clients/ocp/"
    exit 1
fi

OC_VERSION=$(oc version --client | grep "Client Version" | awk '{print $3}' || echo "unknown")
echo "✓ OpenShift CLI (oc) is installed (version: $OC_VERSION)"

# Check CRC status
echo ""
echo "Checking CRC status..."
CRC_STATUS=$(crc status 2>&1 || true)

if echo "$CRC_STATUS" | grep -q "CRC VM.*Running"; then
    echo "✓ CRC VM is running"
else
    echo "✗ Error: CRC VM is not running"
    echo ""
    echo "Current status:"
    echo "$CRC_STATUS"
    echo ""
    echo "Please start CRC with:"
    echo "  crc start"
    echo ""
    echo "Note: CRC requires at least:"
    echo "  - 9 GB of RAM (12 GB recommended)"
    echo "  - 4 CPU cores (6 recommended)"
    echo "  - 35 GB of disk space"
    exit 1
fi

# Check if we can connect to the cluster
echo ""
echo "Verifying cluster connectivity..."
if ! oc cluster-info &> /dev/null; then
    echo "✗ Error: Cannot connect to OpenShift cluster"
    echo ""
    echo "Try logging in with:"
    echo "  eval \$(crc oc-env)"
    echo "  oc login -u kubeadmin https://api.crc.testing:6443"
    echo ""
    echo "Get the kubeadmin password with:"
    echo "  crc console --credentials"
    exit 1
fi

echo "✓ Successfully connected to OpenShift cluster"

# Display cluster info
echo ""
echo "Cluster information:"
oc cluster-info | head -n 1

# Check current user
CURRENT_USER=$(oc whoami 2>/dev/null || echo "unknown")
echo "Current user: $CURRENT_USER"

# Verify we have admin privileges (needed for operator installation)
if ! oc auth can-i create namespaces &> /dev/null; then
    echo ""
    echo "✗ Warning: Current user does not have cluster-admin privileges"
    echo "  Some operations may fail. Consider logging in as kubeadmin:"
    echo "  oc login -u kubeadmin https://api.crc.testing:6443"
    echo ""
    read -p "Continue anyway? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
else
    echo "✓ User has sufficient privileges"
fi

# Display resource information
echo ""
echo "CRC VM resources:"
crc config view | grep -E "(cpus|memory|disk-size)" || true

echo ""
echo "✓ CRC verification complete"
echo ""
echo "Ready to proceed with e2e test setup"
