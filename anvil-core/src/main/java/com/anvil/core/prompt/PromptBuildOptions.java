package com.anvil.core.prompt;

/** Phase 11: per-step prompt assembly tuning for token economy. */
public record PromptBuildOptions(int stepIndex, boolean afterCompaction, boolean omitTools) {

    public static PromptBuildOptions firstStep() {
        return new PromptBuildOptions(0, false, false);
    }

    public boolean includeFullGuidance() {
        return stepIndex == 0 || afterCompaction;
    }
}
