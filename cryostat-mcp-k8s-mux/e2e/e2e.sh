#!/usr/bin/env bash
set -euo pipefail

# Script: e2e.sh
# Purpose: Main orchestrator for e2e test environment setup on OpenShift/CRC

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
echo "║    Cryostat k8s-multi-mcp E2E Test Environment (OpenShift)     ║"
echo "║                                                                ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

print_step "Starting e2e environment setup on OpenShift/CRC..."
echo ""

# Check for required tools
print_step "Checking prerequisites..."
MISSING_TOOLS=()

if ! command -v docker &> /dev/null; then
    MISSING_TOOLS+=("docker")
fi

if ! command -v oc &> /dev/null; then
    MISSING_TOOLS+=("oc")
fi

if ! command -v helm &> /dev/null; then
    MISSING_TOOLS+=("helm")
fi

if ! command -v crc &> /dev/null; then
    MISSING_TOOLS+=("crc")
fi

if [[ ${#MISSING_TOOLS[@]} -gt 0 ]]; then
    print_error "Missing required tools: ${MISSING_TOOLS[*]}"
    echo ""
    echo "Please install the missing tools:"
    echo "  - crc:    https://developers.redhat.com/products/openshift-local/overview"
    echo "  - oc:     Included with CRC or download from https://mirror.openshift.com/pub/openshift-v4/clients/ocp/"
    echo "  - docker: https://docs.docker.com/get-docker/"
    echo "  - helm:   https://helm.sh/docs/intro/install/"
    exit 1
fi

print_success "All prerequisites found"
echo ""

# Run setup scripts in sequence
run_script "01-verify-crc.sh"
run_script "02-install-cert-manager.sh"
run_script "03-install-cryostat-operator.sh"
run_script "04-create-namespaces.sh"
run_script "05-deploy-cryostat-c1.sh"
run_script "06-deploy-cryostat-c2.sh"
run_script "07-deploy-sample-apps.sh"
run_script "08-build-mcp-image.sh"
run_script "09-install-mcp.sh"
run_script "10-verify.sh"

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
echo "The e2e environment is now running on OpenShift/CRC."
echo "All services are accessible via OpenShift Routes."
echo ""
echo "Useful commands:"
echo "  • View MCP logs:   oc logs -n cryostat-multi-mcp -l app.kubernetes.io/name=cryostat-k8s-multi-mcp -f"
echo "  • List Routes:     oc get routes -A | grep -E '(cryostat|mcp)'"
echo "  • Cleanup:         ${SCRIPTS_DIR}/cleanup.sh"
echo ""