#!/usr/bin/env bash
set -euo pipefail

# Script: 09-build-mcp-image.sh
# Purpose: Build and push the k8s-multi-mcp container image to CRC internal registry

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="$(dirname "$SCRIPT_DIR")"
K8S_MULTI_MCP_DIR="$(dirname "$E2E_DIR")"
PROJECT_ROOT="$(dirname "$K8S_MULTI_MCP_DIR")"

# Image configuration
REGISTRY_HOST="default-route-openshift-image-registry.apps-crc.testing"
IMAGE_NAMESPACE="cryostat-multi-mcp"
IMAGE_NAME="cryostat-k8s-multi-mcp"
IMAGE_TAG="latest"
LOCAL_IMAGE="${IMAGE_NAME}:${IMAGE_TAG}"
REGISTRY_IMAGE="${REGISTRY_HOST}/${IMAGE_NAMESPACE}/${IMAGE_NAME}:${IMAGE_TAG}"

echo "=== Building and pushing k8s-multi-mcp container image ==="

# Verify we're connected to OpenShift
if ! oc cluster-info &> /dev/null; then
    echo "✗ Error: Not connected to OpenShift cluster"
    echo "Run 01-verify-crc.sh first"
    exit 1
fi

# Ensure the image namespace exists
echo "Ensuring namespace ${IMAGE_NAMESPACE} exists..."
oc create namespace "${IMAGE_NAMESPACE}" --dry-run=client -o yaml | oc apply -f -

# Navigate to project root
cd "$PROJECT_ROOT"

echo ""
echo "Project root: $PROJECT_ROOT"
echo "K8s-multi-mcp directory: $K8S_MULTI_MCP_DIR"
echo "Building local image: ${LOCAL_IMAGE}"
echo ""

# Build the container image using Maven (local tag)
echo "Building with Maven..."
./mvnw clean package -pl k8s-multi-mcp -am -DskipTests \
    -Dquarkus.container-image.build=true \
    -Dquarkus.container-image.image="${LOCAL_IMAGE}"

if [[ $? -ne 0 ]]; then
    echo "✗ Error: Maven build failed"
    exit 1
fi

echo ""
echo "✓ Image built successfully: ${LOCAL_IMAGE}"

# Verify image exists locally
if ! docker image inspect "$LOCAL_IMAGE" &> /dev/null; then
    echo "✗ Error: Image ${LOCAL_IMAGE} not found after build"
    exit 1
fi

# Tag image for registry
echo ""
echo "Tagging image for OpenShift registry..."
docker tag "${LOCAL_IMAGE}" "${REGISTRY_IMAGE}"

# Ensure OpenShift registry route is exposed
echo ""
echo "Checking OpenShift registry route..."
if ! oc get route default-route -n openshift-image-registry &> /dev/null; then
    echo "Registry route not found, exposing it..."
    oc patch configs.imageregistry.operator.openshift.io/cluster \
        --patch '{"spec":{"defaultRoute":true}}' \
        --type=merge
    
    echo "Waiting for registry route to be created..."
    for i in {1..30}; do
        if oc get route default-route -n openshift-image-registry &> /dev/null; then
            echo "✓ Registry route created"
            break
        fi
        if [ $i -eq 30 ]; then
            echo "✗ Error: Timeout waiting for registry route"
            exit 1
        fi
        sleep 2
    done
else
    echo "✓ Registry route already exists"
fi

# Get the actual registry host from the route
REGISTRY_HOST=$(oc get route default-route -n openshift-image-registry -o jsonpath='{.spec.host}')
echo "Registry host: ${REGISTRY_HOST}"

# Update the registry image tag with the actual host
REGISTRY_IMAGE="${REGISTRY_HOST}/${IMAGE_NAMESPACE}/${IMAGE_NAME}:${IMAGE_TAG}"

# Login to OpenShift registry
echo ""
echo "Logging in to OpenShift internal registry..."
REGISTRY_TOKEN=$(oc whoami -t)
REGISTRY_USER=$(oc whoami)

# Use --tls-verify=false for CRC's self-signed certificate
# Note: docker CLI is actually podman in CRC, which supports this flag
if ! echo "${REGISTRY_TOKEN}" | docker login -u "${REGISTRY_USER}" --password-stdin --tls-verify=false "${REGISTRY_HOST}" 2>/dev/null; then
    echo "✗ Error: Failed to login to OpenShift registry"
    echo ""
    echo "Troubleshooting:"
    echo "1. Check registry route status:"
    echo "   oc get route default-route -n openshift-image-registry"
    echo "2. Check if registry is running:"
    echo "   oc get pods -n openshift-image-registry"
    echo "3. Try manual login:"
    echo "   docker login -u \$(oc whoami) -p \$(oc whoami -t) --tls-verify=false ${REGISTRY_HOST}"
    exit 1
fi

echo "✓ Logged in to registry"

# Push image to registry
echo ""
echo "Pushing image to OpenShift registry: ${REGISTRY_IMAGE}"
# Use --tls-verify=false for CRC's self-signed certificate
if ! docker push --tls-verify=false "${REGISTRY_IMAGE}"; then
    echo "✗ Error: Failed to push image to registry"
    exit 1
fi

echo ""
echo "✓ Image pushed successfully"

# Verify image in registry
echo ""
echo "Verifying image in registry..."
if oc get imagestream "${IMAGE_NAME}" -n "${IMAGE_NAMESPACE}" &> /dev/null; then
    echo "✓ ImageStream found in namespace ${IMAGE_NAMESPACE}"
    oc get imagestream "${IMAGE_NAME}" -n "${IMAGE_NAMESPACE}"
else
    echo "⚠ Warning: ImageStream not found yet (may take a moment to appear)"
fi

echo ""
echo "Image details:"
docker image inspect "${LOCAL_IMAGE}" --format '{{.Id}} {{.Created}} {{.Size}}'

echo ""
echo "✓ Build and push complete"
echo ""
echo "Registry image: ${REGISTRY_IMAGE}"
echo "This image will be used by the Helm chart deployment"
