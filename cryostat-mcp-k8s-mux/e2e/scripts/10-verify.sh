#!/usr/bin/env bash
set -euo pipefail

# Script: 10-verify.sh
# Purpose: Verify all components are running correctly on OpenShift

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== Verifying e2e environment ==="

# Verify we're connected to OpenShift
if ! oc cluster-info &> /dev/null; then
    echo "✗ Error: Not connected to OpenShift cluster"
    exit 1
fi

echo ""
echo "Checking projects..."
REQUIRED_PROJECTS=("cryostat-mcp" "c1" "apps1" "c2" "apps2")
for PROJECT in "${REQUIRED_PROJECTS[@]}"; do
    if oc get project "$PROJECT" &> /dev/null; then
        echo "  ✓ Project '${PROJECT}' exists"
    else
        echo "  ✗ Project '${PROJECT}' missing"
        exit 1
    fi
done

echo ""
echo "Checking Cryostat c1 (project: c1)..."
if oc get pods -n c1 -l app.kubernetes.io/name=cryostat &> /dev/null; then
    READY=$(oc get pods -n c1 -l app.kubernetes.io/name=cryostat -o jsonpath='{.items[0].status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "False")
    if [[ "$READY" == "True" ]]; then
        echo "  ✓ Cryostat c1 is running"
    else
        echo "  ✗ Cryostat c1 is not ready"
        oc get pods -n c1
        exit 1
    fi
else
    echo "  ✗ Cryostat c1 not found"
    exit 1
fi

echo ""
echo "Checking Cryostat c2 (project: c2)..."
if oc get pods -n c2 -l app.kubernetes.io/name=cryostat &> /dev/null; then
    READY=$(oc get pods -n c2 -l app.kubernetes.io/name=cryostat -o jsonpath='{.items[0].status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "False")
    if [[ "$READY" == "True" ]]; then
        echo "  ✓ Cryostat c2 is running"
    else
        echo "  ✗ Cryostat c2 is not ready"
        oc get pods -n c2
        exit 1
    fi
else
    echo "  ✗ Cryostat c2 not found"
    exit 1
fi

echo ""
echo "Checking sample apps..."
if oc get deployment quarkus-cryostat-agent -n apps1 &> /dev/null; then
    AVAILABLE=$(oc get deployment quarkus-cryostat-agent -n apps1 -o jsonpath='{.status.conditions[?(@.type=="Available")].status}')
    if [[ "$AVAILABLE" == "True" ]]; then
        echo "  ✓ Quarkus app (apps1) is running"
    else
        echo "  ✗ Quarkus app (apps1) is not available"
        oc get deployment quarkus-cryostat-agent -n apps1
        exit 1
    fi
else
    echo "  ✗ Quarkus app not found in apps1"
    exit 1
fi

if oc get deployment wildfly-cryostat-agent -n apps2 &> /dev/null; then
    AVAILABLE=$(oc get deployment wildfly-cryostat-agent -n apps2 -o jsonpath='{.status.conditions[?(@.type=="Available")].status}')
    if [[ "$AVAILABLE" == "True" ]]; then
        echo "  ✓ WildFly app (apps2) is running"
    else
        echo "  ✗ WildFly app (apps2) is not available"
        oc get deployment wildfly-cryostat-agent -n apps2
        exit 1
    fi
else
    echo "  ✗ WildFly app not found in apps2"
    exit 1
fi

echo ""
echo "Checking cryostat-mcp-k8s-mux..."
if oc get pods -n cryostat-mcp -l app.kubernetes.io/name=cryostat-mcp-k8s-mux &> /dev/null; then
    READY=$(oc get pods -n cryostat-mcp -l app.kubernetes.io/name=cryostat-mcp-k8s-mux -o jsonpath='{.items[0].status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "False")
    if [[ "$READY" == "True" ]]; then
        echo "  ✓ cryostat-mcp-k8s-mux is running"
    else
        echo "  ✗ cryostat-mcp-k8s-mux is not ready"
        oc get pods -n cryostat-mcp -l app.kubernetes.io/name=cryostat-mcp-k8s-mux
        exit 1
    fi
else
    echo "  ✗ cryostat-mcp-k8s-mux not found"
    oc get all -n cryostat-mcp
    exit 1
fi

echo ""
echo "Checking Routes..."
echo ""

# Check Cryostat c1 Route
if oc get route -n c1 cryostat-c1 &> /dev/null; then
    C1_ROUTE=$(oc get route -n c1 cryostat-c1 -o jsonpath='{.spec.host}')
    echo "  ✓ Cryostat c1 Route: https://${C1_ROUTE}"
    
    # Test Route connectivity
    if curl -k -s -o /dev/null -w "%{http_code}" "https://${C1_ROUTE}/health" | grep -q "200\|401"; then
        echo "    ✓ Route is accessible"
    else
        echo "    ✗ Warning: Route may not be accessible yet"
    fi
else
    echo "  ✗ Cryostat c1 Route not found"
fi

echo ""

# Check Cryostat c2 Route
if oc get route -n c2 cryostat-c2 &> /dev/null; then
    C2_ROUTE=$(oc get route -n c2 cryostat-c2 -o jsonpath='{.spec.host}')
    echo "  ✓ Cryostat c2 Route: https://${C2_ROUTE}"
    
    # Test Route connectivity
    if curl -k -s -o /dev/null -w "%{http_code}" "https://${C2_ROUTE}/health" | grep -q "200\|401"; then
        echo "    ✓ Route is accessible"
    else
        echo "    ✗ Warning: Route may not be accessible yet"
    fi
else
    echo "  ✗ Cryostat c2 Route not found"
fi

echo ""

# Check cryostat-mcp-k8s-mux Route
MCP_ROUTE_NAME=$(oc get route -n cryostat-mcp -o name | head -n 1 | cut -d'/' -f2)
if [ -n "$MCP_ROUTE_NAME" ]; then
    MCP_ROUTE=$(oc get route -n cryostat-mcp "$MCP_ROUTE_NAME" -o jsonpath='{.spec.host}')
    echo "  ✓ cryostat-mcp-k8s-mux Route: http://${MCP_ROUTE}"
    
    # Test Route connectivity
    if curl -s -o /dev/null -w "%{http_code}" "http://${MCP_ROUTE}/q/health" | grep -q "200"; then
        echo "    ✓ Route is accessible"
    else
        echo "    ✗ Warning: Route may not be accessible yet"
    fi
else
    echo "  ✗ cryostat-mcp-k8s-mux Route not found"
fi

echo ""
echo "✓ All components verified successfully!"
echo ""
echo "=== Access Information ==="
echo ""
echo "Cryostat Instances (credentials: user:pass):"
if [ -n "${C1_ROUTE:-}" ]; then
    echo "  - Cryostat c1: https://${C1_ROUTE}"
fi
if [ -n "${C2_ROUTE:-}" ]; then
    echo "  - Cryostat c2: https://${C2_ROUTE}"
fi
echo ""
echo "cryostat-mcp-k8s-mux:"
if [ -n "${MCP_ROUTE:-}" ]; then
    echo "  - MCP Server: http://${MCP_ROUTE}"
    echo "  - Health:     http://${MCP_ROUTE}/q/health"
fi
echo ""
echo "Sample Applications:"
echo "  - Quarkus (apps1): quarkus-cryostat-agent"
echo "  - WildFly (apps2): wildfly-cryostat-agent"
echo ""
echo "To view logs:"
echo "  oc logs -n cryostat-mcp -l app.kubernetes.io/name=cryostat-mcp-k8s-mux -f"