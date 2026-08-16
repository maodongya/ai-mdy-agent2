package com.anvil.core.compact;

/** Per-tool content limits for history injection (Phase 11.2). */
public final class ToolContentBudget {

    private ToolContentBudget() {}

    public static int limitFor(String toolName, int globalMax) {
        if (toolName == null) {
            return globalMax;
        }
        int cap =
                switch (toolName) {
                    case "fs.read", "codebase.search", "symbols.search" -> 2_000;
                    case "grep" -> 4_000;
                    case "diagnostics.collect", "verify.auto", "diagnostics.auto" -> 800;
                    case "shell.exec" -> 3_000;
                    default -> globalMax;
                };
        return Math.min(cap, globalMax);
    }

    public static String apply(String content, String toolName, int globalMax) {
        int limit = limitFor(toolName, globalMax);
        return ContextCompactor.truncateContent(content, limit);
    }
}
