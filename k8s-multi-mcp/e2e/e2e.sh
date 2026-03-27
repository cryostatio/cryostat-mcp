#!/usr/bin/env bash
set -euo pipefail

# Script: e2e.sh
# Purpose: Main orchestrator for e2e test environment setup

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPTS_DIR="${SCRIPT_DIR}/scripts"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored messages
print_step() {
    echo -e "${BLUE}==>${NC} ${1}"
}

print_success() {
    echo -e "${GREEN}✓${NC} ${1}"
}

print_error() {
    echo -e "${RED}✗${NC} ${1}"
}

print_warning() {
    echo -e "${YELLOW}!${NC} ${1}"
}

# Function to run a script and handle errors
run_script() {
    local script_name=$1
    local script_path="${SCRIPTS_DIR}/${script_name}"
    
    if [[ ! -f "$script_path" ]]; then
        print_error "Script not found: ${script_path}"
        exit 1
    fi
    
    print_step "Running ${script_name}..."
    echo ""
    
    if bash "$script_path"; then
        echo ""
        print_success "${script_name} completed successfully"
        echo ""
        return 0
    else
        echo ""
        print_error "${script_name} failed"
        echo ""
        print_warning "To clean up and start over, run: ${SCRIPTS_DIR}/cleanup.sh"
        exit 1
    fi
}

# Main execution
echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                                                                ║"
echo "║         Cryostat k8s-multi-mcp E2E Test Environment           ║"
echo "║                                                                ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

print_step "Starting e2e environment setup..."
echo ""

# Check for required tools
print_step "Checking prerequisites..."
MISSING_TOOLS=()

if ! command -v docker &> /dev/null; then
    MISSING_TOOLS+=("docker")
fi

if ! command -v kubectl &> /dev/null; then
    MISSING_TOOLS+=("kubectl")
fi

if ! command -v helm &> /dev/null; then
    MISSING_TOOLS+=("helm")
fi

if [[ ${#MISSING_TOOLS[@]} -gt 0 ]]; then
    print_error "Missing required tools: ${MISSING_TOOLS[*]}"
    echo ""
    echo "Please install the missing tools and try again."
    exit 1
fi

print_success "All prerequisites found"
echo ""

# Run setup scripts in sequence
run_script "01-setup-kind.sh"
run_script "02-create-cluster.sh"
run_script "03-load-images.sh"
run_script "04-create-namespaces.sh"
run_script "05-add-helm-repo.sh"
run_script "06-install-cryostat-c1.sh"
run_script "07-install-cryostat-c2.sh"
run_script "08-deploy-sample-apps.sh"
run_script "09-build-mcp-image.sh"
run_script "10-install-mcp.sh"
run_script "11-verify.sh"

# Final summary
echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                                                                ║"
echo "║                  E2E Environment Ready!                        ║"
echo "║                                                                ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""
print_success "All components deployed and verified successfully!"
echo ""
echo "Access URLs:"
echo "  • Cryostat c1:     http://localhost:8080 (monitors apps1)"
echo "  • Cryostat c2:     http://localhost:8081 (monitors apps2)"
echo "  • k8s-multi-mcp:   http://localhost:8082"
echo ""
echo "Useful commands:"
echo "  • View MCP logs:   kubectl logs -n cryostat-multi-mcp -l app.kubernetes.io/name=k8s-multi-mcp -f"
echo "  • Test health:     curl http://localhost:8082/q/health"
echo "  • Cleanup:         ${SCRIPTS_DIR}/cleanup.sh"
echo ""