package com.anvil.core.compact;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolContentBudgetTest {

    @Test
    void capsReadLowerThanGlobal() {
        String huge = "x".repeat(10_000);
        String out = ToolContentBudget.apply(huge, "fs.read", 8_000);
        assertTrue(out.length() < 3_000);
        assertTrue(out.contains("truncated"));
    }
}
