package com.anvil.core.orchestrator;

import com.anvil.core.loop.RunProfile;
import com.anvil.tools.PlanTool;

import java.util.Set;

/** Blocks workspace writes until COMPLEX runs produce a plan (Phase 10.2). */
public final class PlannerGate {

    private static final Set<String> WRITE_TOOLS = Set.of(
            "fs.write", "fs.apply_patch", "search_replace", "apply_patch", "edit.plan");

    private PlannerGate() {}

    public static boolean blocksWrite(
            RunProfile profile, boolean plannerRequired, boolean plannerComplete, String toolName) {
        if (!plannerRequired || profile != RunProfile.COMPLEX || plannerComplete) {
            return false;
        }
        if ("plan.update".equals(toolName)) {
            return false;
        }
        return WRITE_TOOLS.contains(toolName);
    }

    public static String blockedMessage() {
        return """
                Planner phase: call plan.update first with structured steps (checkbox or numbered list) \
                in %s before editing source files.
                """
                .formatted(PlanTool.PLAN_PATH)
                .trim();
    }
}
