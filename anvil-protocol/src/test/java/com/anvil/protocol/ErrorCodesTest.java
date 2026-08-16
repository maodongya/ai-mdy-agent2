package com.anvil.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ErrorCodesTest {

    @Test
    void allStableCodesAreDefined() {
        assertNotNull(ErrorCodes.MODEL_UNAVAILABLE);
        assertNotNull(ErrorCodes.MODEL_TIMEOUT);
        assertNotNull(ErrorCodes.MODEL_BAD_RESPONSE);
        assertNotNull(ErrorCodes.TOOL_ARG_INVALID);
        assertNotNull(ErrorCodes.TOOL_TIMEOUT);
        assertNotNull(ErrorCodes.TOOL_FAILED);
        assertNotNull(ErrorCodes.POLICY_DENIED);
        assertNotNull(ErrorCodes.APPROVAL_DENIED);
        assertNotNull(ErrorCodes.APPROVAL_TIMEOUT);
        assertNotNull(ErrorCodes.BUDGET_EXCEEDED);
        assertNotNull(ErrorCodes.CONTEXT_EXHAUSTED);
        assertNotNull(ErrorCodes.WORKSPACE_CONFLICT);
        assertNotNull(ErrorCodes.CANCELLED);
        assertNotNull(ErrorCodes.INTERNAL);
    }
}
