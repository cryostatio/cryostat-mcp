#!/usr/bin/env bash
set -euo pipefail

# Script: 09-build-mcp-image.sh
# Purpose: Build the k8s-multi-mcp container image and load it into kind

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="$(dirname "$SCRIPT_DIR")"
K8S_MULTI_MCP_DIR="$(dirname "$E2E_DIR")"
PROJECT_ROOT="$(dirname "$K8S_MULTI_MCP_DIR")"

CLUSTER_NAME="cryostat-mcp-e2e"
IMAGE_NAME="localhost/cryostat-k8s-multi-mcp"
IMAGE_TAG="latest"
FULL_IMAGE="${IMAGE_NAME}:${IMAGE_TAG}"

echo "=== Building k8s-multi-mcp container image ==="

# Check if cluster exists
if ! kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
    echo "Error: Cluster '${CLUSTER_NAME}' does not exist"
    echo "Run 02-create-cluster.sh first"
    exit 1
fi

# Navigate to project root
cd "$PROJECT_ROOT"

echo "Project root: $PROJECT_ROOT"
echo "K8s-multi-mcp directory: $K8S_MULTI_MCP_DIR"
echo "Building image: ${FULL_IMAGE}"
echo ""

# Build the container image using Maven
echo "Building with Maven..."
./mvnw clean package -pl k8s-multi-mcp -am -DskipTests \
    -Dquarkus.container-image.build=true \
    -Dquarkus.container-image.image="${FULL_IMAGE}"

if [[ $? -ne 0 ]]; then
    echo "Error: Maven build failed"
    exit 1
fi

echo ""
echo "✓ Image built successfully"

# Verify image exists
if ! docker image inspect "$FULL_IMAGE" &> /dev/null; then
    echo "Error: Image ${FULL_IMAGE} not found after build"
    exit 1
fi

echo ""
echo "Loading image into kind cluster..."
kind load docker-image "$FULL_IMAGE" --name "$CLUSTER_NAME"

echo ""
echo "✓ Image loaded into kind cluster"
echo ""
echo "Image details:"
docker image inspect "$FULL_IMAGE" --format '{{.Id}} {{.Created}} {{.Size}}'