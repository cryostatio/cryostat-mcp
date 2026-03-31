# Kubernetes-Native Pod Name Parameters

## Overview

The k8s-multi-mcp now supports Kubernetes-native Pod name parameters for all directed tools. Instead of requiring internal Cryostat identifiers (JVM hash IDs or Target IDs), users can now reference targets using familiar Kubernetes Pod names.

## Features

### 1. Pod Name Resolution
- **Automatic Translation**: Pod names are automatically resolved to Cryostat identifiers
- **Caching**: Results are cached for 60 seconds using Quarkus Cache (Caffeine)
- **Historical Support**: Query terminated Pods using the `useAuditLog` parameter

### 2. Backward Compatibility
- Existing `jvmId` and `targetId` parameters continue to work
- No breaking changes to existing integrations

### 3. Enhanced Error Messages
- Clear, actionable error messages when Pods cannot be found
- Suggestions to use `useAuditLog=true` for terminated Pods

## Usage

### Basic Example

Instead of:
```json
{
  "namespace": "my-app-namespace",
  "jvmId": "abc123def456789"
}
```

You can now use:
```json
{
  "namespace": "my-app-namespace",
  "podName": "my-app-pod-xyz"
}
```

### With Historical Targets (Audit Log)

For terminated or scaled-down Pods:
```json
{
  "namespace": "my-app-namespace",
  "podName": "crashed-pod-123",
  "useAuditLog": true
}
```

## Supported Tools

### Tools with `jvmId` Parameter (5 tools)
These tools support `useAuditLog` for historical queries:

1. **getAuditTarget**
   - `podName` OR `jvmId`
   - `useAuditLog` (optional, default: false)

2. **getAuditTargetLineage**
   - `podName` OR `jvmId`
   - `useAuditLog` (optional, default: false)

3. **listTargetArchivedRecordings**
   - `podName` OR `jvmId`
   - `useAuditLog` (optional, default: false)
   - Useful for accessing archives from terminated Pods

4. **scrapeTargetMetrics**
   - `podName` OR `jvmId`
   - `useAuditLog` (optional, default: false)

5. **executeQuery**
   - `podName` OR `jvmId`
   - `filename` (required)
   - `query` (required)
   - `useAuditLog` (optional, default: false)
   - Query archived recordings from terminated Pods

### Tools with `targetId` Parameter (5 tools)
These tools work with active targets only:

1. **listTargetEventTemplates**
   - `podName` OR `targetId`

2. **getTargetEventTemplate**
   - `podName` OR `targetId`
   - `templateType` (required)
   - `templateName` (required)

3. **listTargetActiveRecordings**
   - `podName` OR `targetId`

4. **startTargetRecording**
   - `podName` OR `targetId`
   - `recordingName` (required)
   - `templateName` (required)
   - `templateType` (required)
   - `duration` (required)

5. **getTargetReport**
   - `podName` OR `targetId`

## Parameter Rules

### Mutual Exclusivity
You must provide **either** `podName` **or** the original identifier (`jvmId`/`targetId`), but not both:

✅ Valid:
```json
{"namespace": "ns1", "podName": "my-pod"}
{"namespace": "ns1", "jvmId": "abc123"}
{"namespace": "ns1", "targetId": 42}
```

❌ Invalid:
```json
{"namespace": "ns1", "podName": "my-pod", "jvmId": "abc123"}
{"namespace": "ns1", "podName": "my-pod", "targetId": 42}
```

### Required Parameters
- `namespace`: Always required (identifies which Cryostat instance to query)
- `podName` OR `jvmId`/`targetId`: One identifier is required

## Use Cases

### 1. Active Pod Monitoring
```json
{
  "tool": "listTargetActiveRecordings",
  "arguments": {
    "namespace": "production",
    "podName": "payment-service-7d9f8b-xyz"
  }
}
```

### 2. Post-Mortem Analysis
Query archives from a crashed Pod:
```json
{
  "tool": "listTargetArchivedRecordings",
  "arguments": {
    "namespace": "production",
    "podName": "crashed-service-abc",
    "useAuditLog": true
  }
}
```

### 3. Historical Investigation
Run SQL queries on archives from terminated Pods:
```json
{
  "tool": "executeQuery",
  "arguments": {
    "namespace": "staging",
    "podName": "old-deployment-pod-123",
    "filename": "my-recording.jfr",
    "query": "SELECT * FROM jdk.GarbageCollection LIMIT 10",
    "useAuditLog": true
  }
}
```

### 4. Starting a Recording
```json
{
  "tool": "startTargetRecording",
  "arguments": {
    "namespace": "development",
    "podName": "test-app-pod-456",
    "recordingName": "performance-test",
    "templateName": "Profiling",
    "templateType": "TARGET",
    "duration": 60
  }
}
```

## Performance Considerations

### Caching Strategy
- **Cache Key**: `(namespace, podName)` only
- **TTL**: 60 seconds
- **Size**: Maximum 1000 entries with LRU eviction
- **Benefit**: `useAuditLog` queries are expensive, but once cached, subsequent queries benefit

### Cache Behavior
```
First query:  podName="my-pod", useAuditLog=true  → Queries audit log, caches result
Second query: podName="my-pod", useAuditLog=false → Uses cached result (no query)
```

The cache doesn't distinguish between active and audit log queries because a Pod's identifiers don't change based on how we query for them.

## Error Handling

### Pod Not Found (Active Query)
```
Error: No target found for Pod 'my-pod' in namespace 'my-namespace'.
Possible reasons:
1. Pod does not exist in the namespace
2. Pod is not a JVM application
3. Cryostat has not discovered the Pod yet
4. Pod name is misspelled
5. Pod may have been terminated (try with useAuditLog=true)
```

### Pod Not Found (Audit Log Query)
```
Error: No target found for Pod 'my-pod' in namespace 'my-namespace'.
Possible reasons:
1. Pod does not exist in the namespace
2. Pod is not a JVM application
3. Cryostat has not discovered the Pod yet
4. Pod name is misspelled
```

### Multiple Targets Warning
If multiple targets match a Pod name (rare), the first match is used and a warning is logged:
```
WARN: Multiple discovery nodes found for Pod 'my-pod' in namespace 'my-namespace' (useAuditLog=false).
      Using first match with target: service:jmx:rmi:///jndi/rmi://my-pod:9091/jmxrmi
```

## Configuration

### Cache Settings
Located in `application.properties`:
```properties
# Pod name resolution cache configuration
quarkus.cache.caffeine."pod-name-resolution".initial-capacity=100
quarkus.cache.caffeine."pod-name-resolution".maximum-size=1000
quarkus.cache.caffeine."pod-name-resolution".expire-after-write=60s
```

### Adjusting Cache Settings
- **Increase TTL**: For more stable environments, increase `expire-after-write`
- **Increase Size**: For clusters with many Pods, increase `maximum-size`
- **Decrease TTL**: For rapidly changing environments, decrease `expire-after-write`

## Implementation Details

### Architecture
```
Client Request (podName)
    ↓
K8sMultiMCP.transformArguments()
    ↓
PodNameResolver.resolvePodNameToJvmId/TargetId()
    ↓
Quarkus Cache (check)
    ↓ (cache miss)
CryostatMCP.listTargets(names=[podName], useAuditLog)
    ↓
Cryostat GraphQL API
    ↓
Return Target {id, jvmId, ...}
    ↓
Cache Result
    ↓
Transform Arguments (replace podName with jvmId/targetId)
    ↓
Invoke Original Tool
```

### Key Components

1. **PodNameResolver** (`PodNameResolver.java`)
   - Handles Pod name to identifier resolution
   - Uses `@CacheResult` for automatic caching
   - Provides both `jvmId` and `targetId` resolution methods

2. **K8sMultiMCP** (`K8sMultiMCP.java`)
   - Adds `podName` and `useAuditLog` parameters to tools
   - Transforms arguments before invoking underlying tools
   - Validates parameter mutual exclusivity

3. **Quarkus Cache** (Caffeine)
   - High-performance, thread-safe caching
   - Configurable TTL and size limits
   - Automatic eviction

## Migration Guide

### For Existing Integrations
No changes required! Existing code using `jvmId` or `targetId` continues to work.

### For New Integrations
Prefer `podName` for better readability and Kubernetes-native experience:

**Before:**
```python
# Need to look up JVM ID first
jvm_id = get_jvm_id_somehow("my-pod")
result = mcp_client.call_tool("listTargetActiveRecordings", {
    "namespace": "production",
    "jvmId": jvm_id
})
```

**After:**
```python
# Direct Pod name usage
result = mcp_client.call_tool("listTargetActiveRecordings", {
    "namespace": "production",
    "podName": "my-pod"
})
```

## Troubleshooting

### Issue: "No target found for Pod"
**Solutions:**
1. Verify Pod exists: `kubectl get pod <pod-name> -n <namespace>`
2. Check if Pod is a JVM application
3. Wait for Cryostat discovery (may take a few seconds)
4. For terminated Pods, use `useAuditLog=true`

### Issue: Cache not working as expected
**Solutions:**
1. Check cache configuration in `application.properties`
2. Verify Quarkus Cache extension is installed
3. Check logs for cache-related warnings

### Issue: Multiple targets warning
**Solutions:**
1. This is usually harmless - the first match is used
2. If problematic, use `jvmId` or `targetId` directly for precise targeting

## Future Enhancements

Potential future improvements:
1. **Namespace Auto-Detection**: Infer namespace from Pod name if unique across monitored namespaces
2. **Pod Label Selectors**: Support selecting Pods by labels
3. **Wildcard Support**: Query multiple Pods with pattern matching
4. **Cache Pre-Warming**: Pre-populate cache on startup for known Pods

## References

- [Cryostat GraphQL Schema](https://github.com/cryostatio/cryostat/blob/main/schema/schema.graphql)
- [Cryostat PR #1445](https://github.com/cryostatio/cryostat/pull/1445) - Added `useAuditLog` parameter
- [Quarkus Cache Guide](https://quarkus.io/guides/cache)