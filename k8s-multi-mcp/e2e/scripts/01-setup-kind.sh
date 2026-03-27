#!/usr/bin/env bash
set -euo pipefail

# Script: 01-setup-kind.sh
# Purpose: Check for kind installation and install if missing

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== Checking kind installation ==="

if command -v kind &> /dev/null; then
    KIND_VERSION=$(kind version | grep -oP 'kind v\K[0-9.]+' || echo "unknown")
    echo "✓ kind is already installed (version: $KIND_VERSION)"
    exit 0
fi

echo "✗ kind is not installed"
echo ""
echo "Installing kind..."

# Detect OS and architecture
OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
ARCH="$(uname -m)"

case "$ARCH" in
    x86_64)
        ARCH="amd64"
        ;;
    aarch64|arm64)
        ARCH="arm64"
        ;;
    *)
        echo "Error: Unsupported architecture: $ARCH"
        exit 1
        ;;
esac

# Download and install kind
KIND_VERSION="v0.20.0"
KIND_URL="https://kind.sigs.k8s.io/dl/${KIND_VERSION}/kind-${OS}-${ARCH}"
INSTALL_DIR="${HOME}/.local/bin"

echo "Downloading kind ${KIND_VERSION} for ${OS}-${ARCH}..."
mkdir -p "$INSTALL_DIR"

if command -v curl &> /dev/null; then
    curl -Lo "${INSTALL_DIR}/kind" "$KIND_URL"
elif command -v wget &> /dev/null; then
    wget -O "${INSTALL_DIR}/kind" "$KIND_URL"
else
    echo "Error: Neither curl nor wget is available"
    exit 1
fi

chmod +x "${INSTALL_DIR}/kind"

# Check if install directory is in PATH
if [[ ":$PATH:" != *":${INSTALL_DIR}:"* ]]; then
    echo ""
    echo "Warning: ${INSTALL_DIR} is not in your PATH"
    echo "Add the following line to your shell profile (~/.bashrc, ~/.zshrc, etc.):"
    echo "  export PATH=\"\${HOME}/.local/bin:\${PATH}\""
    echo ""
    echo "For this session, run:"
    echo "  export PATH=\"${INSTALL_DIR}:\${PATH}\""
fi

echo "✓ kind installed successfully to ${INSTALL_DIR}/kind"
"${INSTALL_DIR}/kind" version