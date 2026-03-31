#!/usr/bin/env bash
set -euo pipefail

# Script: cleanup.sh
# Purpose: Clean up the e2e test environment on OpenShift/CRC

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="$(dirname "$SCRIPT_DIR")"
OPERATOR_NAMESPACE="cryostat-operator-system"

echo "=== Cleaning up e2e environment ==="

# Verify we're connected to OpenShift
if ! oc cluster-info &> /dev/null; then
    echo "✗ Error: Not connected to OpenShift cluster"
    echo "Nothing to clean up"
    exit 0
fi

echo ""
echo "This will delete all e2e test resources from the OpenShift cluster."
echo "The following projects will be deleted:"
echo "  - cryostat-mcp"
echo "  - c1"
echo "  - apps1"
echo "  - c2"
echo "  - apps2"
echo ""
echo "The following components will remain installed:"
echo "  - cert-manager (manifest-based)"
echo "  - cryostat-operator"
echo ""
read -p "Are you sure you want to continue? (y/N) " -n 1 -r
echo

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Cleanup cancelled"
    exit 0
fi

echo ""
echo "Deleting e2e test projects..."

PROJECTS=("cryostat-mcp" "c1" "apps1" "c2" "apps2")

for PROJECT in "${PROJECTS[@]}"; do
    if oc get project "$PROJECT" &> /dev/null; then
        echo "  Deleting project: ${PROJECT}"
        oc delete project "$PROJECT" --wait=false
    else
        echo "  Project not found: ${PROJECT}"
    fi
done

echo ""
echo "Waiting for projects to be deleted..."
echo "This may take a minute..."

for PROJECT in "${PROJECTS[@]}"; do
    if oc get project "$PROJECT" &> /dev/null; then
        echo "  Waiting for ${PROJECT}..."
        oc wait --for=delete project/"$PROJECT" --timeout=120s 2>/dev/null || true
    fi
done

echo ""
echo "✓ Projects deleted successfully"

# Optional: Clean up operators
echo ""
read -p "Do you want to uninstall cert-manager and cryostat-operator? (y/N) " -n 1 -r
echo

if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo ""
    echo "Uninstalling operators..."
    
    # Uninstall Cryostat operator (using operator-sdk)
    oc project "${OPERATOR_NAMESPACE}"
    if oc get csv 2>/dev/null | grep -q "cryostat-operator"; then
        echo "  Uninstalling Cryostat operator..."
        if command -v operator-sdk &> /dev/null; then
            operator-sdk cleanup cryostat-operator || echo "    Warning: operator-sdk cleanup failed"
        else
            echo "    Warning: operator-sdk not found, manual cleanup required"
            echo "    Run: oc delete csv -A -l operators.coreos.com/cryostat-operator"
        fi
    fi
    
    # Uninstall cert-manager (manifest-based)
    if oc get namespace cert-manager &> /dev/null; then
        echo "  Uninstalling cert-manager..."
        oc delete namespace cert-manager --wait=false
    fi
    
    echo "  ✓ Operator uninstallation completed"
fi

# Optional: Clean up local Docker images
echo ""
read -p "Do you want to remove local Docker images used in e2e tests? (y/N) " -n 1 -r
echo

if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo ""
    echo "Removing local images..."
    
    IMAGES=(
        "default-route-openshift-image-registry.apps-crc.testing/cryostat-mcp/cryostat-mcp-k8s-mux:latest"
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
    echo "  docker rmi quay.io/cryostat/cryostat:4.2.0-snapshot"
    echo "  docker rmi quay.io/redhat-java-monitoring/quarkus-cryostat-agent:latest"
    echo "  docker rmi quay.io/redhat-java-monitoring/wildfly-28-cryostat-agent:latest"
fi

echo ""
echo "✓ Cleanup complete"
echo ""
echo "Note: CRC cluster is still running. To stop it, run:"
echo "  crc stop"