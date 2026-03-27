#!/usr/bin/env bash
set -euo pipefail

# Script: 03-load-images.sh
# Purpose: Load container images into the kind cluster

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="$(dirname "$SCRIPT_DIR")"

CLUSTER_NAME="cryostat-mcp-e2e"

echo "=== Loading container images into kind cluster ==="

# Check if cluster exists
if ! kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
    echo "Error: Cluster '${CLUSTER_NAME}' does not exist"
    echo "Run 02-create-cluster.sh first"
    exit 1
fi

# List of images to pre-load
IMAGES=(
    "quay.io/cryostat/cryostat:4.2.0-snapshot"
    "quay.io/redhat-java-monitoring/quarkus-cryostat-agent:latest"
    "quay.io/redhat-java-monitoring/wildfly-28-cryostat-agent:latest"
)

echo "Pre-pulling images to speed up deployment..."
echo ""

for IMAGE in "${IMAGES[@]}"; do
    echo "Processing: ${IMAGE}"
    
    # Check if image exists locally
    if docker image inspect "$IMAGE" &> /dev/null; then
        echo "  ✓ Image already exists locally"
    else
        echo "  → Pulling image..."
        docker pull "$IMAGE"
    fi
    
    echo "  → Loading into kind cluster..."
    kind load docker-image "$IMAGE" --name "$CLUSTER_NAME"
    echo "  ✓ Loaded successfully"
    echo ""
done

echo "✓ All images loaded into kind cluster"
echo ""
echo "Note: The k8s-multi-mcp image will be built and loaded separately by script 09"