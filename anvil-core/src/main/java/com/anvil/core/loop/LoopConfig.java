package com.anvil.core.loop;

/** Loop orchestration tuning (Phase 10 / 11). */
public record LoopConfig(
        boolean parallelReadTools,
        boolean parallelWrites,
        boolean exploreSubAgent,
        boolean plannerRequired,
        int exploreMaxSteps,
        int exploreMaxTokensBudget,
        long tokenBudgetPerRun) {

    public LoopConfig {
        if (exploreMaxSteps <= 0) {
            exploreMaxSteps = 4;
        }
        if (exploreMaxTokensBudget <= 0) {
            exploreMaxTokensBudget = 8_000;
        }
        if (tokenBudgetPerRun <= 0) {
            tokenBudgetPerRun = 500_000L;
        }
    }

    /** Backward-compatible ctor (Phase 5). */
    public LoopConfig(boolean parallelReadTools) {
        this(parallelReadTools, false, false, false, 4, 8_000, 500_000L);
    }

    /** Backward-compatible 5-arg ctor. */
    public LoopConfig(
            boolean parallelReadTools,
            boolean parallelWrites,
            boolean exploreSubAgent,
            boolean plannerRequired,
            int exploreMaxSteps) {
        this(parallelReadTools, parallelWrites, exploreSubAgent, plannerRequired, exploreMaxSteps, 8_000, 500_000L);
    }

    public static LoopConfig defaults() {
        return new LoopConfig(true, true, false, false, 4, 8_000, 500_000L);
    }

    public static LoopConfig disabledParallel() {
        return new LoopConfig(false, false, false, false, 4, 8_000, 500_000L);
    }

    public static LoopConfig forProfile(RunProfile profile) {
        return switch (profile) {
            case COMPLEX -> new LoopConfig(true, true, true, true, 8, 8_000, 500_000L);
            case EXTENDED -> new LoopConfig(true, true, false, false, 4, 8_000, 500_000L);
            case STANDARD -> new LoopConfig(true, false, false, false, 4, 8_000, 500_000L);
        };
    }
}
