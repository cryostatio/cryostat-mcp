#!/usr/bin/env bash
set -euo pipefail

# Script: 11-verify.sh
# Purpose: Verify all components are running correctly

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="$(dirname "$SCRIPT_DIR")"

CLUSTER_NAME="cryostat-mcp-e2e"

echo "=== Verifying e2e environment ==="

# Check if cluster exists
if ! kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
    echo "✗ Cluster '${CLUSTER_NAME}' does not exist"
    exit 1
fi

# Set context
kubectl config use-context "kind-${CLUSTER_NAME}"

echo ""
echo "Checking namespaces..."
REQUIRED_NAMESPACES=("cryostat-multi-mcp" "c1" "apps1" "c2" "apps2")
for NS in "${REQUIRED_NAMESPACES[@]}"; do
    if kubectl get namespace "$NS" &> /dev/null; then
        echo "  ✓ Namespace '${NS}' exists"
    else
        echo "  ✗ Namespace '${NS}' missing"
        exit 1
    fi
done

echo ""
echo "Checking Cryostat c1 (namespace: c1)..."
if kubectl get pods -n c1 -l app.kubernetes.io/name=cryostat &> /dev/null; then
    READY=$(kubectl get pods -n c1 -l app.kubernetes.io/name=cryostat -o jsonpath='{.items[0].status.conditions[?(@.type=="Ready")].status}')
    if [[ "$READY" == "True" ]]; then
        echo "  ✓ Cryostat c1 is running"
    else
        echo "  ✗ Cryostat c1 is not ready"
        kubectl get pods -n c1
        exit 1
    fi
else
    echo "  ✗ Cryostat c1 not found"
    exit 1
fi

echo ""
echo "Checking Cryostat c2 (namespace: c2)..."
if kubectl get pods -n c2 -l app.kubernetes.io/name=cryostat &> /dev/null; then
    READY=$(kubectl get pods -n c2 -l app.kubernetes.io/name=cryostat -o jsonpath='{.items[0].status.conditions[?(@.type=="Ready")].status}')
    if [[ "$READY" == "True" ]]; then
        echo "  ✓ Cryostat c2 is running"
    else
        echo "  ✗ Cryostat c2 is not ready"
        kubectl get pods -n c2
        exit 1
    fi
else
    echo "  ✗ Cryostat c2 not found"
    exit 1
fi

echo ""
echo "Checking sample apps..."
if kubectl get deployment quarkus-cryostat-agent -n apps1 &> /dev/null; then
    AVAILABLE=$(kubectl get deployment quarkus-cryostat-agent -n apps1 -o jsonpath='{.status.conditions[?(@.type=="Available")].status}')
    if [[ "$AVAILABLE" == "True" ]]; then
        echo "  ✓ Quarkus app (apps1) is running"
    else
        echo "  ✗ Quarkus app (apps1) is not available"
        kubectl get deployment quarkus-cryostat-agent -n apps1
        exit 1
    fi
else
    echo "  ✗ Quarkus app not found in apps1"
    exit 1
fi

if kubectl get deployment wildfly-cryostat-agent -n apps2 &> /dev/null; then
    AVAILABLE=$(kubectl get deployment wildfly-cryostat-agent -n apps2 -o jsonpath='{.status.conditions[?(@.type=="Available")].status}')
    if [[ "$AVAILABLE" == "True" ]]; then
        echo "  ✓ WildFly app (apps2) is running"
    else
        echo "  ✗ WildFly app (apps2) is not available"
        kubectl get deployment wildfly-cryostat-agent -n apps2
        exit 1
    fi
else
    echo "  ✗ WildFly app not found in apps2"
    exit 1
fi

echo ""
echo "Checking k8s-multi-mcp..."
if kubectl get pods -n cryostat-multi-mcp -l app.kubernetes.io/name=cryostat-k8s-multi-mcp &> /dev/null; then
    READY=$(kubectl get pods -n cryostat-multi-mcp -l app.kubernetes.io/name=cryostat-k8s-multi-mcp -o jsonpath='{.items[0].status.conditions[?(@.type=="Ready")].status}')
    if [[ "$READY" == "True" ]]; then
        echo "  ✓ k8s-multi-mcp is running"
    else
        echo "  ✗ k8s-multi-mcp is not ready"
        kubectl get pods -n cryostat-multi-mcp -l app.kubernetes.io/name=cryostat-k8s-multi-mcp
        exit 1
    fi
else
    echo "  ✗ k8s-multi-mcp not found"
    kubectl get all -n cryostat-multi-mcp
    exit 1
fi

echo ""
echo "✓ All components verified successfully!"
echo ""
echo "=== Next Steps ==="
echo ""
echo "To access services from outside the cluster, run:"
echo "  ./scripts/12-port-forward.sh"
echo ""
echo "This will expose:"
echo "  - k8s-multi-mcp:  http://localhost:8082"
echo "  - Cryostat c1:    http://localhost:8080 (user:pass)"
echo "  - Cryostat c2:    http://localhost:8081 (user:pass)"
echo ""
echo "Sample Applications:"
echo "  - Quarkus (apps1): quarkus-cryostat-agent"
echo "  - WildFly (apps2): wildfly-cryostat-agent"
echo ""
echo "To view logs:"
echo "  kubectl logs -n cryostat-multi-mcp -l app.kubernetes.io/name=cryostat-k8s-multi-mcp -f"