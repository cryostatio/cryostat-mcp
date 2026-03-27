#!/usr/bin/env bash
set -euo pipefail

# Script: 12-port-forward.sh
# Purpose: Set up port forwarding for testing services from outside the kind cluster

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLUSTER_NAME="cryostat-mcp-e2e"

# Default ports - can be overridden by environment variables
MCP_PORT="${MCP_PORT:-9082}"
C1_PORT="${C1_PORT:-9080}"
C2_PORT="${C2_PORT:-9081}"

echo "=== Setting up port forwarding ==="

# Check if cluster exists
if ! kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
    echo "Error: Cluster '${CLUSTER_NAME}' does not exist"
    echo "Run 02-create-cluster.sh first"
    exit 1
fi

# Set context
kubectl config use-context "kind-${CLUSTER_NAME}"

# Check if ports are available
echo ""
echo "Checking port availability..."
echo "Using ports: MCP=$MCP_PORT, C1=$C1_PORT, C2=$C2_PORT"
PORTS_IN_USE=()

check_port() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1 ; then
        local process=$(lsof -Pi :$port -sTCP:LISTEN -t 2>/dev/null | head -1)
        local cmdline=""
        if [ -n "$process" ] && [ -f "/proc/$process/cmdline" ]; then
            cmdline=$(cat /proc/$process/cmdline 2>/dev/null | tr '\0' ' ')
        fi
        
        # Check if it's a kubectl port-forward process
        if echo "$cmdline" | grep -q "kubectl.*port-forward"; then
            echo "  Port $port: kubectl port-forward (can be killed with: pkill -f 'kubectl port-forward')"
            PORTS_IN_USE+=("$port")
        # Check if it's rootlessport (kind cluster port mapping)
        elif echo "$cmdline" | grep -q "rootlessport"; then
            echo "  Port $port: kind cluster port mapping (rootlessport)"
            echo "    Note: This is expected if kind cluster has port mappings configured"
            echo "    You can use different ports by setting environment variables:"
            echo "      MCP_PORT=9082 C1_PORT=9080 C2_PORT=9081 $0"
            PORTS_IN_USE+=("$port")
        else
            echo "  Port $port: in use by process $process ($cmdline)"
            PORTS_IN_USE+=("$port")
        fi
    fi
}

check_port $MCP_PORT
check_port $C1_PORT
check_port $C2_PORT

if [ ${#PORTS_IN_USE[@]} -gt 0 ]; then
    echo ""
    echo "✗ Some ports are already in use"
    echo ""
    echo "Options:"
    echo "  1. Use different ports (recommended):"
    echo "     MCP_PORT=9082 C1_PORT=9080 C2_PORT=9081 $0"
    echo ""
    echo "  2. Kill existing kubectl port-forwards:"
    echo "     pkill -f 'kubectl port-forward'"
    echo ""
    echo "  3. If ports are used by kind's rootlessport, either:"
    echo "     - Use different ports (option 1)"
    echo "     - Or access services via kind's port mappings directly"
    exit 1
fi

echo "✓ All ports available"
echo ""
echo "Starting port forwarding in background..."
echo "Press Ctrl+C to stop all port forwards"
echo ""

# Function to cleanup port forwards on exit
cleanup() {
    echo ""
    echo "Stopping port forwards..."
    jobs -p | xargs -r kill 2>/dev/null || true
    echo "✓ Port forwards stopped"
}
trap cleanup EXIT INT TERM

# Port forward k8s-multi-mcp
echo "Forwarding k8s-multi-mcp (localhost:$MCP_PORT -> svc:8080)..."
kubectl port-forward -n cryostat-multi-mcp \
    svc/k8s-multi-mcp-cryostat-k8s-multi-mcp $MCP_PORT:8080 &
MCP_PID=$!

# Port forward Cryostat c1
echo "Forwarding Cryostat c1 (localhost:$C1_PORT -> svc:8181)..."
kubectl port-forward -n c1 \
    svc/cryostat-c1 $C1_PORT:8181 &
C1_PID=$!

# Port forward Cryostat c2
echo "Forwarding Cryostat c2 (localhost:$C2_PORT -> svc:8181)..."
kubectl port-forward -n c2 \
    svc/cryostat-c2 $C2_PORT:8181 &
C2_PID=$!

# Wait for port forwards to establish
echo ""
echo "Waiting for port forwards to establish..."
sleep 3

# Check if port forwards are running
if ! kill -0 $MCP_PID 2>/dev/null; then
    echo "✗ Failed to start k8s-multi-mcp port forward"
    echo "Check if port $MCP_PORT is already in use or if the service exists"
    exit 1
fi

if ! kill -0 $C1_PID 2>/dev/null; then
    echo "✗ Failed to start Cryostat c1 port forward"
    echo "Check if port $C1_PORT is already in use or if the service exists"
    exit 1
fi

if ! kill -0 $C2_PID 2>/dev/null; then
    echo "✗ Failed to start Cryostat c2 port forward"
    echo "Check if port $C2_PORT is already in use or if the service exists"
    exit 1
fi

echo ""
echo "✓ Port forwarding active"
echo ""
echo "=== Service URLs ==="
echo ""
echo "k8s-multi-mcp:     http://localhost:$MCP_PORT"
echo "Cryostat c1:       http://localhost:$C1_PORT (user:pass)"
echo "Cryostat c2:       http://localhost:$C2_PORT (user:pass)"
echo ""
echo "=== Test Commands ==="
echo ""
echo "# Test k8s-multi-mcp health:"
echo "curl http://localhost:$MCP_PORT/q/health"
echo ""
echo "# Test k8s-multi-mcp readiness:"
echo "curl http://localhost:$MCP_PORT/q/health/ready"
echo ""
echo "# Test Cryostat c1 (with basic auth):"
echo "curl -u user:pass http://localhost:$C1_PORT/health"
echo ""
echo "# Test Cryostat c2 (with basic auth):"
echo "curl -u user:pass http://localhost:$C2_PORT/health"
echo ""
echo "Port forwarding will remain active until you press Ctrl+C"
echo ""

# Wait indefinitely
wait