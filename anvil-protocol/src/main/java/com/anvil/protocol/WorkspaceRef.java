package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 工作区引用：描述一个线程所绑定的工作区位置与沙箱权限。
 *
 * @param root          工作区根目录绝对路径
 * @param sandboxTier   沙箱隔离层级（默认 {@link SandboxTier#WORKSPACE_WRITE}）
 * @param knowledgeRoot 知识库根目录（可空，用于引用外部知识资源）
 */
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
