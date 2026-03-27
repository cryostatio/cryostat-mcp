#!/usr/bin/env bash
set -euo pipefail

# Script: 05-add-helm-repo.sh
# Purpose: Add the Cryostat Helm repository

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="$(dirname "$SCRIPT_DIR")"

REPO_NAME="cryostat"
REPO_URL="https://cryostat.io/helm-charts"

echo "=== Adding Cryostat Helm repository ==="

# Check if helm is installed
if ! command -v helm &> /dev/null; then
    echo "Error: helm is not installed"
    echo "Please install helm: https://helm.sh/docs/intro/install/"
    exit 1
fi

echo "Helm version:"
helm version --short

echo ""
echo "Checking if repository '${REPO_NAME}' is already added..."

if helm repo list 2>/dev/null | grep -q "^${REPO_NAME}[[:space:]]"; then
    echo "✓ Repository '${REPO_NAME}' already exists"
    echo ""
    echo "Updating repository..."
    helm repo update "$REPO_NAME"
else
    echo "Adding repository '${REPO_NAME}' from ${REPO_URL}..."
    helm repo add "$REPO_NAME" "$REPO_URL"
    echo "✓ Repository added successfully"
    echo ""
    echo "Updating repository..."
    helm repo update "$REPO_NAME"
fi

echo ""
echo "✓ Helm repository ready"
echo ""
echo "Available Cryostat charts:"
helm search repo "$REPO_NAME" --versions | head -10
