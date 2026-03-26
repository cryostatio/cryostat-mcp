package io.cryostat.mcp.k8s;

import java.util.Set;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "k8s-multi-mcp")
public interface K8sMultiMCPConfig {

    /** Tools that require namespace parameter (directed operations). */
    Set<String> namespaceRequiredTools();

    /** Tools that accept optional namespace parameter (non-directed operations). */
    Set<String> namespaceOptionalTools();
}
