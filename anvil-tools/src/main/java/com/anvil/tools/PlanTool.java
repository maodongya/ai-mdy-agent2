package com.anvil.tools;

import com.anvil.protocol.ToolResult;

/** Writes `.anvil/plan.md` (Plan mode exception in PolicyEngine). */
public final class PlanTool {

    public static final String PLAN_PATH = ".anvil/plan.md";

    private PlanTool() {}

    public static ToolResult update(String toolCallId, FsTools fs, String content) {
        return fs.write(toolCallId, PLAN_PATH, content == null ? "" : content);
    }
}
