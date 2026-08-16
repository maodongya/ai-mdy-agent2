package com.anvil.core.trace;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceComparatorTest {

    @Test
    void comparesEventSequences() {
        List<String> expected = List.of("run.started", "tool.completed", "run.completed");
        List<String> actual = List.of("run.started", "step.started", "tool.completed", "run.completed");
        TraceComparator.TraceDiff diff = TraceComparator.compare(expected, actual);
        assertEquals(3, diff.expectedCount());
        assertTrue(diff.similarity() >= 0.66);
        assertTrue(diff.missingInActual().isEmpty());
    }

    @Test
    void detectsMissingEvents() {
        TraceComparator.TraceDiff diff =
                TraceComparator.compare(List.of("verify.completed"), List.of("run.completed"));
        assertTrue(diff.missingInActual().contains("verify.completed"));
    }
}
