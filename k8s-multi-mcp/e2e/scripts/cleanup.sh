#!/usr/bin/env bash
set -euo pipefail

# Script: cleanup.sh
# Purpose: Clean up the e2e test environment

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="$(dirname "$SCRIPT_DIR")"

CLUSTER_NAME="cryostat-mcp-e2e"

echo "=== Cleaning up e2e environment ==="

# Check if cluster exists
if ! kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
    echo "Cluster '${CLUSTER_NAME}' does not exist, nothing to clean up"
    exit 0
fi

echo ""
echo "This will delete the kind cluster '${CLUSTER_NAME}' and all its resources."
read -p "Are you sure you want to continue? (y/N) " -n 1 -r
echo

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Cleanup cancelled"
    exit 0
fi

echo ""
echo "Deleting kind cluster '${CLUSTER_NAME}'..."
kind delete cluster --name "$CLUSTER_NAME"

echo ""
echo "✓ Cluster deleted successfully"

# Optional: Clean up local Docker images
echo ""
read -p "Do you want to remove local Docker images used in e2e tests? (y/N) " -n 1 -r
echo

if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo ""
    echo "Removing local images..."
    
    IMAGES=(
        "localhost/cryostat-k8s-multi-mcp:latest"
    )
    
    for IMAGE in "${IMAGES[@]}"; do
        if docker image inspect "$IMAGE" &> /dev/null; then
            echo "  Removing: ${IMAGE}"
            docker rmi "$IMAGE" || echo "    Warning: Failed to remove ${IMAGE}"
        else
            echo "  Image not found: ${IMAGE}"
        fi
    done
    
    echo ""
    echo "Note: Pre-pulled images (Cryostat, sample apps) were not removed."
    echo "To remove them manually, run:"
    echo "  docker rmi quay.io/cryostat/cryostat:4.2.0-SNAPSHOT"
    echo "  docker rmi quay.io/redhat-java-monitoring/quarkus-cryostat-agent:latest"
    echo "  docker rmi quay.io/redhat-java-monitoring/wildfly-28-cryostat-agent:latest"
fi

echo ""
echo "✓ Cleanup complete"