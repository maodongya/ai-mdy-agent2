package com.anvil.core.loop;

/** Loop orchestration tuning (Phase 10). */
public record LoopConfig(
        boolean parallelReadTools,
        boolean parallelWrites,
        boolean exploreSubAgent,
        boolean plannerRequired,
        int exploreMaxSteps) {

    public LoopConfig {
        if (exploreMaxSteps <= 0) {
            exploreMaxSteps = 6;
        }
    }

    /** Backward-compatible ctor (Phase 5). */
    public LoopConfig(boolean parallelReadTools) {
        this(parallelReadTools, false, false, false, 6);
    }

    public static LoopConfig defaults() {
        return new LoopConfig(true, true, true, false, 6);
    }

    public static LoopConfig disabledParallel() {
        return new LoopConfig(false, false, false, false, 6);
    }

    public static LoopConfig forProfile(RunProfile profile) {
        return switch (profile) {
            case COMPLEX -> new LoopConfig(true, true, true, true, 8);
            case EXTENDED -> new LoopConfig(true, true, true, false, 6);
            case STANDARD -> new LoopConfig(true, false, false, false, 6);
        };
    }
}
