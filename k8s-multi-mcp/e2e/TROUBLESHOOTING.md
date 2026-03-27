# E2E Test Harness Troubleshooting
## k8s-multi-mcp Issues

### Issue: Pod in CreateContainerConfigError - Secret Not Found

**Symptom:**
```
pod/k8s-multi-mcp-cryostat-k8s-multi-mcp-xxxxx   0/1     CreateContainerConfigError
```

**Error in pod describe:**
```
Error: secret "k8s-multi-mcp-cryostat-k8s-multi-mcp-credentials" not found
```

**Root Cause:**
The Helm chart requires `auth.authorizationHeader` to be set in the values file to create the credentials secret. Without this value, the secret is not created but the deployment still references it.

**Solution:**
Add the `auth.authorizationHeader` field to your Helm values file with the base64-encoded basic auth credentials (user:pass):
```yaml
auth:
  # Basic auth credentials for connecting to Cryostat instances (user:pass)
  authorizationHeader: "Basic dXNlcjpwYXNz"
```

Then upgrade or reinstall the Helm release:
```bash
helm upgrade k8s-multi-mcp ./k8s-multi-mcp/chart \
    --namespace cryostat-mcp-system \
    --values k8s-multi-mcp/e2e/helm-values/k8s-multi-mcp-values.yaml
```

**Status:** ✅ Fixed in [`k8s-multi-mcp-values.yaml`](helm-values/k8s-multi-mcp-values.yaml)

---


## OAuth2-Proxy Permission Issues

### Problem
The oauth2-proxy container crashes with "permission denied" when trying to read the htpasswd file:
```
[main.go:59] ERROR: Failed to initialise OAuth2 Proxy: could not validate htpasswd: could not load htpasswd file: could not open htpasswd file: open /etc/oauth2_proxy/basicauth/htpasswd: permission denied
```

### Root Cause
The Cryostat Helm chart mounts the htpasswd secret with `defaultMode: 288` (octal 0440), which means:
- Owner: read (4)
- Group: read (4)
- Others: no access (0)

However, the oauth2-proxy container runs as a non-root user (UID 65532, GID 65532) and is not in the owner or group, so it cannot read the file.

### Solution
The installation scripts (06-install-cryostat-c1.sh and 07-install-cryostat-c2.sh) automatically patch the deployment after Helm installation to set `defaultMode: 292` (octal 0444), which makes the file world-readable:
- Owner: read (4)
- Group: read (4)
- Others: read (4)

The scripts use a JSON patch to directly replace the defaultMode value:
```bash
# Find the index of the htpasswd volume
VOLUME_INDEX=$(kubectl get deployment -n c1 cryostat-c1-v4 -o json | jq '.spec.template.spec.volumes | map(.name) | index("cryostat-c1-htpasswd")')

# Apply JSON patch to replace the defaultMode value
kubectl patch deployment -n c1 cryostat-c1-v4 --type=json -p="[{\"op\": \"replace\", \"path\": \"/spec/template/spec/volumes/$VOLUME_INDEX/secret/defaultMode\", \"value\": 292}]"
```

This triggers an automatic rollout with new pods that have the correct permissions.

### Manual Fix
If you need to apply this fix manually to an existing deployment:

1. Find the volume index:
```bash
VOLUME_INDEX=$(kubectl get deployment -n c1 cryostat-c1-v4 -o json | jq '.spec.template.spec.volumes | map(.name) | index("cryostat-c1-htpasswd")')
echo "Volume index: $VOLUME_INDEX"
```

2. Apply the JSON patch:
```bash
kubectl patch deployment -n c1 cryostat-c1-v4 --type=json -p="[{\"op\": \"replace\", \"path\": \"/spec/template/spec/volumes/$VOLUME_INDEX/secret/defaultMode\", \"value\": 292}]"
```

3. Wait for the rollout to complete:
```bash
kubectl rollout status deployment -n c1 cryostat-c1-v4
```

4. Verify the fix:
```bash
kubectl get deployment -n c1 cryostat-c1-v4 -o yaml | grep -A 3 "name: cryostat-c1-htpasswd"
```

You should see `defaultMode: 292` in the output.

### Verification
After applying the fix, verify that the oauth2-proxy container starts successfully:
```bash
kubectl get pods -n c1
kubectl logs -n c1 <pod-name> --container=cryostat-authproxy
```

The logs should show oauth2-proxy starting without permission errors.

### Why JSON Patch Instead of Strategic Merge?
We use `--type=json` instead of `--type=strategic` because:
1. Strategic merge patches try to merge the volume definition, but since `defaultMode` already exists in the Helm-created deployment, Kubernetes doesn't replace it
2. JSON patches directly replace the specific value at the given path
3. This ensures the `defaultMode` value is actually updated to 292

### Implementation
The fix is implemented in:
- [`scripts/06-install-cryostat-c1.sh`](scripts/06-install-cryostat-c1.sh)
- [`scripts/07-install-cryostat-c2.sh`](scripts/07-install-cryostat-c2.sh)

Both scripts apply the patch immediately after Helm installation and wait for the rollout to complete before proceeding.

## "Too Many Open Files" Error

### Problem
The oauth2-proxy container may crash with:
```
ERROR: Failed to initialise OAuth2 Proxy: could not validate htpasswd: could not watch htpasswd file: failed to create watcher for '/etc/oauth2_proxy/basicauth/htpasswd': too many open files
```

### Root Cause
This is a system resource limit issue. The kind cluster (running in Docker/Podman) has limited inotify instances (default: 128). When multiple Cryostat instances are deployed, oauth2-proxy containers may exhaust the available file watchers.

### Solution
Increase the inotify limits on the host system:

```bash
# Temporary fix (until reboot)
sudo sysctl fs.inotify.max_user_instances=512
sudo sysctl fs.inotify.max_user_watches=524288

# Permanent fix
echo "fs.inotify.max_user_instances=512" | sudo tee -a /etc/sysctl.conf
echo "fs.inotify.max_user_watches=524288" | sudo tee -a /etc/sysctl.conf
sudo sysctl -p
```

After increasing the limits, restart the affected pods:
```bash
kubectl delete pod -n c2 -l app.kubernetes.io/name=cryostat
```

### Alternative Workaround
If you cannot modify system limits, deploy only one Cryostat instance at a time for testing, or use a simpler authentication method that doesn't require file watching.

---

## Port Forwarding Issues

### Issue: Ports Used by Kind's rootlessport

**Symptom:**
```
✗ The following ports are already in use: 8080 8081 8082
  Port 8080: kind cluster port mapping (rootlessport)
```

When checking what's using the port:
```bash
$ lsof -i :8080
COMMAND      PID    USER   FD   TYPE  DEVICE SIZE/OFF NODE NAME
rootlessp 377719 aazores    9u  IPv6 4882137      0t0  TCP *:webcache (LISTEN)
```

**Root Cause:**
The kind cluster uses `rootlessport` to map container ports to the host. If the kind cluster configuration includes port mappings (e.g., for ingress on ports 80/443), these may conflict with the ports the port-forward script tries to use (8080, 8081, 8082).

**Solution:**
The port-forward script ([`scripts/12-port-forward.sh`](scripts/12-port-forward.sh)) now:
1. Detects when ports are used by rootlessport
2. Provides helpful error messages
3. Supports using alternative ports via environment variables

**Recommended Fix - Use Alternative Ports:**
```bash
# Use different ports (9080, 9081, 9082 instead of 8080, 8081, 8082)
MCP_PORT=9082 C1_PORT=9080 C2_PORT=9081 ./k8s-multi-mcp/e2e/scripts/12-port-forward.sh
```

The script will then forward to the alternative ports:
```
k8s-multi-mcp:     http://localhost:9082
Cryostat c1:       http://localhost:9080 (user:pass)
Cryostat c2:       http://localhost:9081 (user:pass)
```

**Alternative Solutions:**

1. **Access via kind's port mappings** (if configured):
   - Check if services are already accessible via kind's port mappings
   - This depends on your kind cluster configuration

2. **Modify kind cluster configuration:**
   - Remove or change port mappings in [`manifests/kind-config.yaml`](manifests/kind-config.yaml)
   - Recreate the cluster with `99-cleanup.sh` then `e2e.sh`

3. **Kill rootlessport** (not recommended):
   - This will break the kind cluster's networking
   - Only do this if you're planning to recreate the cluster anyway

**Status:** ✅ Fixed in [`12-port-forward.sh`](scripts/12-port-forward.sh) - script now detects rootlessport and supports alternative ports via environment variables

---

### Issue: Port Already in Use by kubectl port-forward

**Symptom:**
```
Unable to listen on port 9080: Listeners failed to create with the following errors: [unable to create listener: Error listen tcp4 127.0.0.1:9080: bind: address already in use]
```

**Root Cause:**
Previous kubectl port-forward sessions may still be running in the background.

**Solution:**

1. Kill existing kubectl port-forward processes:
```bash
pkill -f 'kubectl port-forward'
```

2. Or identify and kill specific processes:
```bash
# Find process using port 9080
lsof -i :9080
# Kill the process (replace PID with actual process ID)
kill <PID>
```

3. Verify ports are free:
```bash
lsof -i :9080
lsof -i :9081
lsof -i :9082
```

4. Run the port-forward script again:
```bash
MCP_PORT=9082 C1_PORT=9080 C2_PORT=9081 ./k8s-multi-mcp/e2e/scripts/12-port-forward.sh
```

**Status:** ✅ Script detects kubectl port-forward processes and provides helpful error messages