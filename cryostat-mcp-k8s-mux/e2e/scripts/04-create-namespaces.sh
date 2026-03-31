#!/usr/bin/env bash
set -euo pipefail

# Script: 04-create-namespaces.sh
# Purpose: Create all required OpenShift projects for the e2e test

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== Creating OpenShift projects ==="

# Verify we're connected to OpenShift
if ! oc cluster-info &> /dev/null; then
    echo "✗ Error: Not connected to OpenShift cluster"
    echo "Run 01-verify-crc.sh first"
    exit 1
fi

# List of projects to create
PROJECTS=(
    "cryostat-mcp"  # cryostat-mcp-k8s-mux deployment
    "c1"            # Cryostat instance 1
    "apps1"         # Applications monitored by c1
    "c2"            # Cryostat instance 2
    "apps2"         # Applications monitored by c2
)

echo "Creating projects..."
echo ""

for PROJECT in "${PROJECTS[@]}"; do
    if oc get project "$PROJECT" &> /dev/null; then
        echo "  ✓ Project '${PROJECT}' already exists"
    else
        echo "  → Creating project '${PROJECT}'..."
        oc new-project "$PROJECT" --description="E2E test project for ${PROJECT}" || {
            echo "  ✗ Failed to create project '${PROJECT}'"
            echo "  Trying with kubectl create namespace..."
            oc create namespace "$PROJECT"
        }
        echo "  ✓ Created successfully"
    fi
done

echo ""
echo "✓ All projects created"
echo ""
echo "Project list:"
oc get projects | grep -E "(NAME|cryostat-mcp|^c1|^c2|apps1|apps2)"

echo ""
echo "Note: In OpenShift, projects are namespaces with additional features"
echo "You can use 'oc project <name>' to switch between projects"