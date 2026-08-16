package com.anvil.core.loop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveRunTrackerTest {

    @AfterEach
    void cleanup() {
        ActiveRunTracker.untrack("run_test");
    }

    @Test
    void cancelMarksRunContextCancelled() {
        RunContext ctx = new RunContext("thr", "run_test");
        ActiveRunTracker.track(ctx);

        assertTrue(ActiveRunTracker.cancel("run_test"));
        assertTrue(ctx.isCancelled());

        ActiveRunTracker.untrack("run_test");
        assertFalse(ActiveRunTracker.cancel("run_test"));
    }
}
