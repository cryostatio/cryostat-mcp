# Plan: Add Kubernetes-Native Pod Name Parameters to Multi-MCP Tools

## Overview

Enhance the k8s-multi-mcp to expose more Kubernetes-native MCP Tools by wrapping the existing sub-MCP Tools and replacing Cryostat JVM hash ID parameters with Kubernetes Pod name parameters. The multi-mcp will internally use the Pod name provided by the client to look up the corresponding JVM hash ID or Target ID in the Cryostat API, then forward the request to the wrapped Tool.

## Dependencies

### Maven Dependency Addition
Add the Quarkus Cache extension (Caffeine) to `k8s-multi-mcp/pom.xml`:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-cache</artifactId>
</dependency>
```

### Cache Configuration
Add to `k8s-multi-mcp/src/main/resources/application.properties`:

```properties
# Pod name resolution cache configuration
quarkus.cache.caffeine."pod-name-resolution".initial-capacity=100
quarkus.cache.caffeine."pod-name-resolution".maximum-size=1000
quarkus.cache.caffeine."pod-name-resolution".expire-after-access=10m
```

## Goals

1. **Kubernetes-Native Interface**: Allow users to reference targets by Pod name instead of internal Cryostat identifiers
2. **Backward Compatibility**: Keep existing jvmId/targetId parameters working alongside new podName parameter
3. **Transparent Translation**: Automatically resolve Pod names to JVM IDs/Target IDs using Cryostat's GraphQL API
4. **Namespace-Scoped**: Maintain required namespace parameter for proper Cryostat instance selection
5. **Historical Target Support**: Support resolution of Pod names for crashed/scaled-down Pods using audit log queries

## Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      MCP Client                              │
│  (provides: namespace="apps1", podName="my-app-pod-xyz")   │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    K8sMultiMCP                               │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  registerDirectedTool()                               │  │
│  │  - Adds podName as optional parameter                 │  │
│  │  - Validates mutual exclusivity (podName XOR jvmId)  │  │
│  └──────────────────────────────────────────────────────┘  │
│                         │                                    │
│                         ▼                                    │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  PodNameResolver                                      │  │
│  │  - resolvePodNameToJvmId(namespace, podName)         │  │
│  │  - resolvePodNameToTargetId(namespace, podName)      │  │
│  │  - Uses CryostatGraphQLClient.targetNodes()          │  │
│  └──────────────────────────────────────────────────────┘  │
│                         │                                    │
└─────────────────────────┼────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│              CryostatGraphQLClient                           │
│  targetNodes(filter: {names: ["my-app-pod-xyz"]})          │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                 Cryostat Instance                            │
│  Returns: DiscoveryNode with Target {id, jvmId, ...}       │
└─────────────────────────────────────────────────────────────┘
```

### Key Components

#### 1. PodNameResolver Service
**Location**: `k8s-multi-mcp/src/main/java/io/cryostat/mcp/k8s/PodNameResolver.java`

**Responsibilities**:
- Resolve Pod names to JVM hash IDs
- Resolve Pod names to Target IDs
- Cache resolution results for performance
- Handle resolution errors gracefully

**Key Methods**:
```java
public class PodNameResolver {
    String resolvePodNameToJvmId(String namespace, String podName);
    Long resolvePodNameToTargetId(String namespace, String podName);
    Optional<Target> findTargetByPodName(String namespace, String podName);
}
```

#### 2. Enhanced K8sMultiMCP
**Location**: `k8s-multi-mcp/src/main/java/io/cryostat/mcp/k8s/K8sMultiMCP.java`

**Changes**:
- Inject PodNameResolver
- Modify `registerDirectedTool()` to add optional `podName` parameter
- Add argument transformation logic in tool handler
- Validate parameter mutual exclusivity

#### 3. CryostatGraphQLClient Enhancement
**Location**: `cryostat-mcp-core/src/main/java/io/cryostat/mcp/CryostatGraphQLClient.java`

**Current**: Already has `targetNodes(DiscoveryNodeFilter filter)` method
**Usage**: Filter by `names` parameter to find Pod by name

## Implementation Details

### Phase 1: Core Resolution Logic

#### Step 1: Update CryostatMCP.listTargets() to Support useAuditLog

The `listTargets()` method in CryostatMCP already includes the `useAuditLog` parameter (added in Cryostat PR #1445). This parameter enables querying historical targets from the audit log, which is essential for resolving Pod names of crashed or scaled-down Pods.

**Key Points**:
- `useAuditLog=false` (default): Query only currently active targets
- `useAuditLog=true`: Query historical targets from audit log (more expensive)
- Useful for tools like `executeQuery` that work with archived recordings from terminated Pods

#### Step 2: Create PodNameResolver Service with Quarkus Cache and Audit Log Support
```java
@ApplicationScoped
public class PodNameResolver {
    
    private static final Logger LOG = Logger.getLogger(PodNameResolver.class);
    
    @Inject CryostatMCPInstanceManager instanceManager;
    
    /**
     * Resolve a Pod name to its JVM hash ID.
     * Results are cached for 60 seconds using Quarkus Cache (Caffeine).
     * Cache is shared regardless of useAuditLog value - once resolved, the result is cached.
     *
     * @param namespace The namespace containing the Pod
     * @param podName The name of the Pod
     * @return The JVM hash ID
     * @throws IllegalArgumentException if no target found for the Pod
     */
    @CacheResult(cacheName = "pod-name-resolution")
    public String resolvePodNameToJvmId(
            @CacheKey String namespace,
            @CacheKey String podName) {
        return resolvePodNameToJvmIdInternal(namespace, podName, false);
    }
    
    /**
     * Resolve a Pod name to its JVM hash ID with explicit audit log control.
     * Results are cached for 60 seconds using Quarkus Cache (Caffeine).
     * Cache is shared regardless of useAuditLog value - once resolved, the result is cached.
     *
     * @param namespace The namespace containing the Pod
     * @param podName The name of the Pod
     * @param useAuditLog Whether to query historical targets from audit log
     * @return The JVM hash ID
     * @throws IllegalArgumentException if no target found for the Pod
     */
    @CacheResult(cacheName = "pod-name-resolution")
    public String resolvePodNameToJvmId(
            @CacheKey String namespace,
            @CacheKey String podName,
            boolean useAuditLog) {
        return resolvePodNameToJvmIdInternal(namespace, podName, useAuditLog);
    }
    
    /**
     * Internal method that performs the actual resolution.
     * Not cached directly - caching happens at the public method level.
     */
    private String resolvePodNameToJvmIdInternal(
            String namespace,
            String podName,
            boolean useAuditLog) {
        LOG.debugf("Resolving Pod '%s' in namespace '%s' to jvmId (useAuditLog=%s)",
            podName, namespace, useAuditLog);
        
        Target target = findTargetByPodName(namespace, podName, useAuditLog)
            .orElseThrow(() -> new IllegalArgumentException(
                "No target found for Pod '" + podName + "' in namespace '" + namespace + "'. " +
                "Possible reasons:\n" +
                "1. Pod does not exist in the namespace\n" +
                "2. Pod is not a JVM application\n" +
                "3. Cryostat has not discovered the Pod yet\n" +
                "4. Pod name is misspelled\n" +
                (useAuditLog ? "" : "5. Pod may have been terminated (try with useAuditLog=true)")));
        
        LOG.debugf("Resolved Pod '%s' to jvmId '%s'", podName, target.jvmId());
        return target.jvmId();
    }
    
    /**
     * Resolve a Pod name to its Target ID.
     * Results are cached for 60 seconds using Quarkus Cache (Caffeine).
     * Cache is shared regardless of useAuditLog value - once resolved, the result is cached.
     *
     * @param namespace The namespace containing the Pod
     * @param podName The name of the Pod
     * @return The Target ID
     * @throws IllegalArgumentException if no target found for the Pod
     */
    @CacheResult(cacheName = "pod-name-resolution")
    public Long resolvePodNameToTargetId(
            @CacheKey String namespace,
            @CacheKey String podName) {
        return resolvePodNameToTargetIdInternal(namespace, podName, false);
    }
    
    /**
     * Resolve a Pod name to its Target ID with explicit audit log control.
     * Results are cached for 60 seconds using Quarkus Cache (Caffeine).
     * Cache is shared regardless of useAuditLog value - once resolved, the result is cached.
     *
     * @param namespace The namespace containing the Pod
     * @param podName The name of the Pod
     * @param useAuditLog Whether to query historical targets from audit log
     * @return The Target ID
     * @throws IllegalArgumentException if no target found for the Pod
     */
    @CacheResult(cacheName = "pod-name-resolution")
    public Long resolvePodNameToTargetId(
            @CacheKey String namespace,
            @CacheKey String podName,
            boolean useAuditLog) {
        return resolvePodNameToTargetIdInternal(namespace, podName, useAuditLog);
    }
    
    /**
     * Internal method that performs the actual resolution.
     * Not cached directly - caching happens at the public method level.
     */
    private Long resolvePodNameToTargetIdInternal(
            String namespace,
            String podName,
            boolean useAuditLog) {
        LOG.debugf("Resolving Pod '%s' in namespace '%s' to targetId (useAuditLog=%s)",
            podName, namespace, useAuditLog);
        
        Target target = findTargetByPodName(namespace, podName, useAuditLog)
            .orElseThrow(() -> new IllegalArgumentException(
                "No target found for Pod '" + podName + "' in namespace '" + namespace + "'. " +
                "Possible reasons:\n" +
                "1. Pod does not exist in the namespace\n" +
                "2. Pod is not a JVM application\n" +
                "3. Cryostat has not discovered the Pod yet\n" +
                "4. Pod name is misspelled\n" +
                (useAuditLog ? "" : "5. Pod may have been terminated (try with useAuditLog=true)")));
        
        LOG.debugf("Resolved Pod '%s' to targetId %d", podName, target.id());
        return target.id();
    }
    
    /**
     * Invalidate cached resolution for a specific Pod.
     * Useful when a Pod is known to have changed.
     */
    @CacheInvalidate(cacheName = "pod-name-resolution")
    public void invalidateCache(
            @CacheKey String namespace,
            @CacheKey String podName) {
        LOG.debugf("Invalidating cache for Pod '%s' in namespace '%s'", podName, namespace);
    }
    
    /**
     * Invalidate all cached resolutions.
     */
    @CacheInvalidateAll(cacheName = "pod-name-resolution")
    public void invalidateAllCache() {
        LOG.debug("Invalidating all Pod name resolution cache entries");
    }
    
    private Optional<Target> findTargetByPodName(String namespace, String podName, boolean useAuditLog) {
        try {
            CryostatMCP mcp = instanceManager.createInstance(namespace);
            
            // Query Cryostat for targets matching the Pod name
            // useAuditLog=true enables querying historical targets from audit log
            List<io.cryostat.mcp.model.graphql.DiscoveryNode> nodes =
                mcp.listTargets(null, null, List.of(podName), null, useAuditLog);
            
            // Find the first node with a target
            Optional<Target> target = nodes.stream()
                .filter(node -> node.target() != null)
                .map(io.cryostat.mcp.model.graphql.DiscoveryNode::target)
                .findFirst();
            
            if (nodes.size() > 1 && target.isPresent()) {
                LOG.warnf(
                    "Multiple discovery nodes found for Pod '%s' in namespace '%s' (useAuditLog=%s). " +
                    "Using first match with target: %s",
                    podName, namespace, useAuditLog, target.get().connectUrl());
            }
            
            return target;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to find target for Pod '%s' in namespace '%s' (useAuditLog=%s)",
                podName, namespace, useAuditLog);
            return Optional.empty();
        }
    }
}
```

**Key Features of Quarkus Cache Implementation**:
- `@CacheResult`: Automatically caches method results based on parameters
- `@CacheKey`: Marks parameters used as cache keys (namespace + podName only)
- **Important**: `useAuditLog` is NOT a cache key - once a Pod is resolved, the result is cached regardless of how it was found
- `@CacheInvalidate`: Allows manual cache invalidation for specific entries
- `@CacheInvalidateAll`: Clears entire cache
- Caffeine backend provides high-performance, thread-safe caching
- TTL configured in application.properties (60 seconds)
- Automatic eviction based on size and time

**Cache Strategy Rationale**:
- A Pod's JVM ID and Target ID don't change based on how we query for them
- Whether found via active query or audit log, the identifiers are the same
- Caching by (namespace, podName) only avoids redundant queries
- If a Pod is found in audit log, subsequent active queries will use the cached result
- This provides better performance and consistency

#### Step 3: Add useAuditLog Parameter to Directed Tools

For tools that work with historical data (especially archived recordings), add an optional `useAuditLog` parameter:

```java
// Add optional useAuditLog parameter for tools that may need historical lookups
toolBuilder.addArgument("useAuditLog",
    "Query historical targets from audit log. Useful for accessing data from " +
    "terminated Pods (e.g., archived recordings). More expensive than active queries.",
    false, Boolean.class);
```

**Tools that should support useAuditLog**:
- `listTargetArchivedRecordings(jvmId)` - Archives may exist from terminated Pods
- `executeQuery(jvmId, filename, query)` - Query archives from terminated Pods
- `scrapeTargetMetrics(jvmId)` - Historical metrics from terminated Pods
- `getAuditTarget(jvmId)` - Already designed for audit log queries
- `getAuditTargetLineage(jvmId)` - Already designed for audit log queries

#### Step 4: Update registerDirectedTool() with useAuditLog Support

### Phase 2: Tool Registration Enhancement

```java
private void registerDirectedTool(Method method, Tool toolAnnotation, String toolName) {
    String description = buildDirectedToolDescription(toolAnnotation.description());
    var toolBuilder = toolManager.newTool(toolName).setDescription(description);
    
    // Add required namespace parameter
    toolBuilder.addArgument("namespace",
        "The namespace of the Cryostat instance to query (required).",
        true, String.class);
    
    // Add optional podName parameter
    toolBuilder.addArgument("podName",
        "The Kubernetes Pod name to identify the target. " +
        "Mutually exclusive with jvmId/targetId parameters. " +
        "If provided, will be resolved to the appropriate identifier. " +
        "For terminated Pods, use with useAuditLog=true.",
        false, String.class);
    
    // Add optional useAuditLog parameter for applicable tools
    if (toolSupportsAuditLog(toolName)) {
        toolBuilder.addArgument("useAuditLog",
            "Query historical targets from audit log. Useful when working with " +
            "terminated Pods (e.g., accessing archived recordings). More expensive than active queries.",
            false, Boolean.class);
    }
    
    // Add original method parameters (jvmId, targetId, etc.)
    Parameter[] parameters = method.getParameters();
    for (Parameter param : parameters) {
        ToolArg argAnnotation = param.getAnnotation(ToolArg.class);
        if (argAnnotation == null) continue;
        
        String paramName = param.getName();
        Class<?> paramType = getArgumentType(param.getType());
        
        // Make jvmId/targetId optional when podName is available
        boolean required = argAnnotation.required() && 
            !paramName.equals("jvmId") && !paramName.equals("targetId");
        
        toolBuilder.addArgument(paramName, argAnnotation.description(), 
            required, paramType);
    }
    
    // Set handler with argument transformation
    toolBuilder.setHandler(toolArgs -> {
        try {
            Map<String, Object> transformedArgs = 
                transformArguments(method, toolArgs.args());
            Object result = invokeDirectedTool(method, transformedArgs, null);
            String jsonResult = result instanceof String ? 
                (String) result : objectMapper.writeValueAsString(result);
            return ToolResponse.success(jsonResult);
        } catch (Exception e) {
            // Error handling...
        }
    });
    
    toolBuilder.register();
}
```

#### Step 5: Implement Argument Transformation with Audit Log Support
```java
private Map<String, Object> transformArguments(
        Method method, Map<String, Object> args) {
    
    String namespace = (String) args.get("namespace");
    String podName = (String) args.get("podName");
    Boolean useAuditLog = (Boolean) args.get("useAuditLog");
    
    // Default useAuditLog to false if not provided
    if (useAuditLog == null) {
        useAuditLog = false;
    }
    
    // Validate mutual exclusivity
    if (podName != null) {
        if (args.containsKey("jvmId") && args.get("jvmId") != null) {
            throw new IllegalArgumentException(
                "Cannot specify both podName and jvmId");
        }
        if (args.containsKey("targetId") && args.get("targetId") != null) {
            throw new IllegalArgumentException(
                "Cannot specify both podName and targetId");
        }
    }
    
    // If podName provided, resolve it
    if (podName != null && !podName.isEmpty()) {
        Map<String, Object> transformed = new HashMap<>(args);
        
        // Determine which parameter the method needs
        Parameter[] parameters = method.getParameters();
        for (Parameter param : parameters) {
            String paramName = param.getName();
            
            if (paramName.equals("jvmId")) {
                // Use audit log if requested for historical Pod lookups
                String jvmId = podNameResolver.resolvePodNameToJvmId(
                    namespace, podName, useAuditLog);
                transformed.put("jvmId", jvmId);
                LOG.debugf("Resolved Pod '%s' to jvmId '%s' (useAuditLog=%s)",
                    podName, jvmId, useAuditLog);
            } else if (paramName.equals("targetId")) {
                // Use audit log if requested for historical Pod lookups
                Long targetId = podNameResolver.resolvePodNameToTargetId(
                    namespace, podName, useAuditLog);
                transformed.put("targetId", targetId);
                LOG.debugf("Resolved Pod '%s' to targetId %d (useAuditLog=%s)",
                    podName, targetId, useAuditLog);
            }
        }
        
        // Remove podName and useAuditLog from args passed to underlying method
        // (useAuditLog was only for resolution, not for the underlying tool)
        transformed.remove("podName");
        transformed.remove("useAuditLog");
        return transformed;
    }
    
    // If useAuditLog was provided but podName wasn't, remove it
    // (it's only relevant for Pod name resolution)
    if (args.containsKey("useAuditLog")) {
        Map<String, Object> transformed = new HashMap<>(args);
        transformed.remove("useAuditLog");
        return transformed;
    }
    
    return args;
}

/**
 * Determines if a tool should support the useAuditLog parameter.
 * Tools that work with historical data (archives, audit queries) benefit from this.
 */
private boolean toolSupportsAuditLog(String toolName) {
    return toolName.equals("listTargetArchivedRecordings") ||
           toolName.equals("executeQuery") ||
           toolName.equals("scrapeTargetMetrics") ||
           toolName.equals("getAuditTarget") ||
           toolName.equals("getAuditTargetLineage");
}
```

### Phase 3: Testing

#### Unit Tests
1. **PodNameResolverTest**
   - Test successful resolution of Pod name to jvmId (active targets)
   - Test successful resolution of Pod name to targetId (active targets)
   - Test resolution with useAuditLog=true (historical targets)
   - Test cache behavior: verify useAuditLog doesn't affect cache key
   - Test that audit log resolution is cached and reused for active queries
   - Test error handling for non-existent Pods
   - Test cache expiration
   - Test manual cache invalidation

2. **K8sMultiMCPTest** (enhanced)
   - Test podName parameter acceptance
   - Test useAuditLog parameter acceptance
   - Test mutual exclusivity validation (podName vs jvmId/targetId)
   - Test argument transformation with useAuditLog
   - Test backward compatibility with jvmId/targetId
   - Test toolSupportsAuditLog() logic

#### Integration Tests
1. Test end-to-end Pod name resolution in e2e environment
2. Verify tools work with both podName and jvmId/targetId
3. Test useAuditLog with terminated Pods
4. Test archived recording queries from terminated Pods
5. Test error messages for invalid Pod names
6. Test fallback behavior (active → audit log)

### Phase 4: Documentation

#### Update Tool Descriptions
Each directed tool description should include:
```
"... Namespace parameter is required to identify the Cryostat instance managing the target.
You can identify the target using either:
- podName: The Kubernetes Pod name (e.g., 'my-app-pod-xyz')
- jvmId: The Cryostat JVM hash ID
- targetId: The Cryostat Target numeric ID
Only one identifier should be provided."
```

#### Update README
Add section explaining:
- How to use Pod names instead of JVM IDs
- Examples of both approaches
- Performance considerations (caching)
- Troubleshooting Pod name resolution

## Tools Affected

Based on CryostatMCP analysis, these tools will gain podName support:

### Tools using `jvmId` parameter:
1. `getAuditTarget(jvmId)` → `getAuditTarget(podName OR jvmId, useAuditLog?)`
2. `getAuditTargetLineage(jvmId)` → `getAuditTargetLineage(podName OR jvmId, useAuditLog?)`
3. `listTargetArchivedRecordings(jvmId)` → `listTargetArchivedRecordings(podName OR jvmId, useAuditLog?)`
4. `scrapeTargetMetrics(jvmId)` → `scrapeTargetMetrics(podName OR jvmId, useAuditLog?)`
5. `executeQuery(jvmId, ...)` → `executeQuery(podName OR jvmId, ..., useAuditLog?)`

### Tools using `targetId` parameter:
1. `listTargetEventTemplates(targetId)` → `listTargetEventTemplates(podName OR targetId)`
2. `getTargetEventTemplate(targetId, ...)` → `getTargetEventTemplate(podName OR targetId, ...)`
3. `listTargetActiveRecordings(targetId)` → `listTargetActiveRecordings(podName OR targetId)`
4. `startTargetRecording(targetId, ...)` → `startTargetRecording(podName OR targetId, ...)`
5. `getTargetReport(targetId)` → `getTargetReport(podName OR targetId)`

**Note**: Tools marked with `useAuditLog?` support the optional useAuditLog parameter for querying historical/terminated Pods.

## Error Handling

### Resolution Failures
```java
throw new IllegalArgumentException(
    "No target found for Pod '" + podName + "' in namespace '" + namespace + "'. " +
    "Possible reasons:\n" +
    "1. Pod does not exist in the namespace\n" +
    "2. Pod is not a JVM application\n" +
    "3. Cryostat has not discovered the Pod yet\n" +
    "4. Pod name is misspelled"
);
```

### Multiple Matches
If multiple targets match the Pod name (unlikely but possible):
```java
throw new IllegalStateException(
    "Multiple targets found for Pod '" + podName + "' in namespace '" + namespace + "'. " +
    "This is unexpected. Please use jvmId or targetId directly."
);
```

## Performance Considerations

1. **Caching with Quarkus Cache (Caffeine)**:
   - Automatic caching of Pod name → Target mappings
   - 60-second TTL configured in application.properties
   - Maximum 1000 entries with LRU eviction
   - Thread-safe and high-performance
   - Supports manual invalidation when needed

2. **Lazy Resolution**: Only resolve when podName is provided

3. **Batch Operations**: For non-directed tools, resolution happens per-instance

4. **GraphQL Efficiency**: Use targeted queries with name filter

5. **Cache Warming**: Consider pre-warming cache on startup for known Pods (optional future enhancement)

## Migration Path

1. **Phase 1**: Add podName support alongside existing parameters (this plan)
2. **Phase 2**: Update documentation and examples to prefer podName
3. **Phase 3**: Consider deprecation notices for direct jvmId/targetId usage (future)
4. **Phase 4**: Potentially make jvmId/targetId internal-only (far future)

## Success Criteria

- [ ] All tools accepting jvmId/targetId also accept podName
- [ ] Tools working with historical data support useAuditLog parameter
- [ ] Mutual exclusivity validation works correctly
- [ ] Pod name resolution is accurate and performant (with caching)
- [ ] useAuditLog enables querying terminated Pods
- [ ] Backward compatibility maintained
- [ ] Comprehensive test coverage (including audit log scenarios)
- [ ] Clear documentation and examples
- [ ] Error messages are helpful and actionable

## Timeline Estimate

- Phase 0 (Dependencies): 0.5 hours (add quarkus-cache, configure)
- Phase 1 (Core Resolution): 2-3 hours (PodNameResolver with @CacheResult and useAuditLog)
- Phase 2 (Tool Enhancement): 2-3 hours (K8sMultiMCP modifications with useAuditLog support)
- Phase 3 (Testing): 3-4 hours (including cache behavior and audit log tests)
- Phase 4 (Documentation): 1 hour

**Total**: 8.5-11.5 hours of development time

## Testing Cache Behavior

Unit tests should verify:
1. Cache hits return cached values without calling Cryostat
2. Cache misses trigger Cryostat queries
3. Cache expiration after 60 seconds
4. Manual cache invalidation works correctly
5. Concurrent access is thread-safe

Example test structure:
```java
@QuarkusTest
class PodNameResolverTest {
    
    @Inject PodNameResolver resolver;
    
    @InjectMock CryostatMCPInstanceManager instanceManager;
    
    @Test
    void testCacheHit() {
        // First call - cache miss
        String jvmId1 = resolver.resolvePodNameToJvmId("ns1", "pod1");
        verify(instanceManager, times(1)).createInstance("ns1");
        
        // Second call - cache hit
        String jvmId2 = resolver.resolvePodNameToJvmId("ns1", "pod1");
        verify(instanceManager, times(1)).createInstance("ns1"); // Still only 1 call
        
        assertEquals(jvmId1, jvmId2);
    }
    
    @Test
    void testCacheInvalidation() {
        resolver.resolvePodNameToJvmId("ns1", "pod1");
        resolver.invalidateCache("ns1", "pod1");
        resolver.resolvePodNameToJvmId("ns1", "pod1");
        
        verify(instanceManager, times(2)).createInstance("ns1"); // 2 calls after invalidation
    }
}
```