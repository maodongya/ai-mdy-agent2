package com.anvil.core.loop;

import com.anvil.core.compact.ContextBudget;
import com.anvil.protocol.Mode;
import com.anvil.protocol.SandboxTier;

import java.util.List;
import java.util.Map;

public record LoopOptions(
        ContextBudget contextBudget,
        SandboxTier sandboxTier,
        String gitBranch,
        List<Map<String, Object>> toolSchemas,
        com.anvil.core.mcp.McpBridge mcpBridge,
        RunProfile runProfile,
        boolean autoApprovePatchTools,
        boolean autoApproveWrites,
        VerifyConfig verifyConfig,
        LoopConfig loopConfig) {

    public LoopOptions {
        if (contextBudget == null) {
            contextBudget = ContextBudget.standard();
        }
        if (sandboxTier == null) {
            sandboxTier = SandboxTier.WORKSPACE_WRITE;
        }
        if (gitBranch == null) {
            gitBranch = "unknown";
        }
        if (toolSchemas == null) {
            toolSchemas = List.of();
        } else {
            toolSchemas = List.copyOf(toolSchemas);
        }
        if (runProfile == null) {
            runProfile = RunProfile.STANDARD;
        }
        if (verifyConfig == null) {
            verifyConfig = VerifyConfig.disabled();
        }
        if (loopConfig == null) {
            loopConfig = LoopConfig.disabledParallel();
        }
    }

    public LoopOptions(
            int compactThresholdTokens,
            SandboxTier sandboxTier,
            String gitBranch,
            List<Map<String, Object>> toolSchemas) {
        this(new ContextBudget(compactThresholdTokens, 0, 0, 0), sandboxTier, gitBranch, toolSchemas, null, RunProfile.STANDARD, true, false, VerifyConfig.disabled(), LoopConfig.disabledParallel());
    }

    public LoopOptions(
            int compactThresholdTokens,
            SandboxTier sandboxTier,
            String gitBranch,
            List<Map<String, Object>> toolSchemas,
            com.anvil.core.mcp.McpBridge mcpBridge) {
        this(new ContextBudget(compactThresholdTokens, 0, 0, 0), sandboxTier, gitBranch, toolSchemas, mcpBridge, RunProfile.STANDARD, true, false, VerifyConfig.disabled(), LoopConfig.disabledParallel());
    }

    public int compactThresholdTokens() {
        return contextBudget.compactThresholdTokens();
    }

    public static LoopOptions defaults(Mode mode) {
        RunProfile profile = RunProfile.defaultFor(mode);
        return new LoopOptions(profile.contextBudget(), SandboxTier.WORKSPACE_WRITE, "unknown", List.of(), null, profile, true, false, VerifyConfig.disabled(), LoopConfig.disabledParallel());
    }

    public static LoopOptions forProfile(RunProfile profile, List<Map<String, Object>> toolSchemas, com.anvil.core.mcp.McpBridge mcpBridge) {
        return new LoopOptions(profile.contextBudget(), SandboxTier.WORKSPACE_WRITE, "unknown", toolSchemas, mcpBridge, profile, true, false, VerifyConfig.disabled(), LoopConfig.disabledParallel());
    }

    public static LoopOptions forProfile(
            RunProfile profile,
            List<Map<String, Object>> toolSchemas,
            com.anvil.core.mcp.McpBridge mcpBridge,
            boolean autoApprovePatchTools) {
        return forProfile(profile, toolSchemas, mcpBridge, autoApprovePatchTools, false);
    }

    public static LoopOptions forProfile(
            RunProfile profile,
            List<Map<String, Object>> toolSchemas,
            com.anvil.core.mcp.McpBridge mcpBridge,
            boolean autoApprovePatchTools,
            boolean autoApproveWrites) {
        return new LoopOptions(
                profile.contextBudget(),
                SandboxTier.WORKSPACE_WRITE,
                "unknown",
                toolSchemas,
                mcpBridge,
                profile,
                autoApprovePatchTools,
                autoApproveWrites,
                VerifyConfig.disabled(),
                LoopConfig.disabledParallel());
    }

    public boolean parallelReadTools() {
        return loopConfig.parallelReadTools();
    }
}
