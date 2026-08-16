package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkspaceRef(
        String root,
        @JsonProperty("sandbox_tier") SandboxTier sandboxTier,
        @JsonProperty("knowledge_root") String knowledgeRoot) {

    public WorkspaceRef {
        if (root == null || root.isBlank()) {
            throw new IllegalArgumentException("root is required");
        }
        if (sandboxTier == null) {
            sandboxTier = SandboxTier.WORKSPACE_WRITE;
        }
    }
}
