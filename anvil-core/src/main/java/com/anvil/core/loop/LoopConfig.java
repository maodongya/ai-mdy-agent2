package com.anvil.core.loop;

/** Loop tuning (Phase 5). */
public record LoopConfig(boolean parallelReadTools) {

    public LoopConfig {
        // default on
    }

    public static LoopConfig defaults() {
        return new LoopConfig(true);
    }

    public static LoopConfig disabledParallel() {
        return new LoopConfig(false);
    }
}
