package com.anvil.core.loop;

import com.anvil.protocol.ErrorCodes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunContextTest {

    @Test
    void abortsAfterConsecutiveWriteFailures() {
        RunContext ctx = new RunContext("t", "r");
        for (int i = 0; i < 4; i++) {
            ctx.recordWriteToolOutcome(false);
            assertFalse(ctx.shouldAbortRun());
        }
        ctx.recordWriteToolOutcome(false);
        assertTrue(ctx.shouldAbortRun());
        assertEquals(ErrorCodes.TOOL_FAILED, ctx.abortCode());
        assertTrue(ctx.abortMessage().contains("5 times"));
    }

    @Test
    void resetsWriteFailureCounterAfterSuccess() {
        RunContext ctx = new RunContext("t", "r");
        ctx.recordWriteToolOutcome(false);
        ctx.recordWriteToolOutcome(false);
        ctx.recordWriteToolOutcome(true);
        ctx.recordWriteToolOutcome(false);
        assertFalse(ctx.shouldAbortRun());
    }

    @Test
    void abortsAfterRepeatedVerifyFixTextOnlyRetries() {
        RunContext ctx = new RunContext("t", "r");
        ctx.setVerifyFixRequired(true);
        for (int i = 0; i < 2; i++) {
            ctx.recordVerifyFixTextOnlyRetry();
            assertFalse(ctx.shouldAbortRun());
        }
        ctx.recordVerifyFixTextOnlyRetry();
        assertTrue(ctx.shouldAbortRun());
    }
}
