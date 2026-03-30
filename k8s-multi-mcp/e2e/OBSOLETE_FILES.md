# Obsolete Files to Remove

The following files are no longer needed after the migration from kind to CRC/OpenShift and should be deleted:

## Scripts to Remove

1. **scripts/01-setup-kind.sh** - Replaced by `01-verify-crc.sh`
2. **scripts/02-create-cluster.sh** - No longer needed (CRC managed by user)
3. **scripts/03-load-images.sh** - No longer needed (images loaded differently in CRC)
4. **scripts/05-add-helm-repo.sh** - No longer needed (using Operator instead of Helm charts)
5. **scripts/06-install-cryostat-c1.sh** - Replaced by `05-deploy-cryostat-instances.sh`
6. **scripts/07-install-cryostat-c2.sh** - Replaced by `05-deploy-cryostat-instances.sh`
7. **scripts/12-port-forward.sh** - No longer needed (using OpenShift Routes)

## Manifests to Remove

1. **manifests/kind-config.yaml** - No longer needed (CRC configuration)

## Helm Values to Remove

1. **helm-values/cryostat-c1-values.yaml** - Replaced by `manifests/cryostat-c1.yaml` (CR)
2. **helm-values/cryostat-c2-values.yaml** - Replaced by `manifests/cryostat-c2.yaml` (CR)

## Removal Command

To remove all obsolete files, run:

```bash
cd k8s-multi-mcp/e2e
rm -f \
  scripts/01-setup-kind.sh \
  scripts/02-create-cluster.sh \
  scripts/03-load-images.sh \
  scripts/05-add-helm-repo.sh \
  scripts/06-install-cryostat-c1.sh \
  scripts/07-install-cryostat-c2.sh \
  scripts/12-port-forward.sh \
  manifests/kind-config.yaml \
  helm-values/cryostat-c1-values.yaml \
  helm-values/cryostat-c2-values.yaml
```

## New File Structure

After removal, the e2e directory structure will be:

```
e2e/
├── e2e.sh                          # Main orchestrator (updated for CRC)
├── README.md                       # Documentation (to be updated)
├── SUMMARY.md                      # Architecture summary (to be created)
├── TROUBLESHOOTING.md             # Troubleshooting guide (to be updated)
├── OBSOLETE_FILES.md              # This file (can be removed after cleanup)
├── manifests/
│   ├── cryostat-c1.yaml           # NEW: Cryostat CR for c1
│   ├── cryostat-c2.yaml           # NEW: Cryostat CR for c2
│   ├── sample-app-apps1.yaml      # Unchanged
│   └── sample-app-apps2.yaml      # Unchanged
├── helm-values/
│   └── k8s-multi-mcp-values.yaml  # Updated for OpenShift Routes
└── scripts/
    ├── 01-verify-crc.sh           # NEW: Verify CRC is running
    ├── 02-install-cert-manager.sh # NEW: Install cert-manager operator
    ├── 03-install-cryostat-operator.sh # NEW: Install Cryostat operator
    ├── 04-create-namespaces.sh    # Updated for OpenShift projects
    ├── 05-deploy-cryostat-instances.sh # NEW: Deploy Cryostat CRs
    ├── 08-deploy-sample-apps.sh   # Updated for OpenShift
    ├── 09-build-mcp-image.sh      # Updated for CRC
    ├── 10-install-mcp.sh          # Updated for OpenShift
    ├── 11-verify.sh               # Updated to check Routes
    └── cleanup.sh                 # Updated for CRC cleanup