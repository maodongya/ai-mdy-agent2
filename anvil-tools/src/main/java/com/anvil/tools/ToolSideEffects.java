package com.anvil.tools;

import com.anvil.protocol.SideEffect;

/** Side-effect classification for built-in tools. */
public final class ToolSideEffects {

    private ToolSideEffects() {}

    public static SideEffect forTool(String toolName) {
        if (toolName != null && toolName.startsWith("mcp.")) {
            return SideEffect.EXTERNAL_SIDE_EFFECT;
        }
        return switch (toolName) {
            case "fs.read", "fs.glob", "grep", "codebase.search", "symbols.search", "git.status", "git.diff", "diagnostics.collect" -> SideEffect.READ;
            case "fs.write", "fs.apply_patch", "search_replace", "apply_patch", "plan.update" -> SideEffect.WRITE_WORKSPACE;
            case "shell.exec" -> SideEffect.EXEC;
            default -> SideEffect.EXTERNAL_SIDE_EFFECT;
        };
    }
}
