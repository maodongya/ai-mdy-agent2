package com.anvil.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffEngineTest {

    @Test
    void detectsAddedLine() {
        List<DiffReviewPanel.DiffRow> rows = DiffEngine.lines("a\nb", "a\nb\nc");
        long added = rows.stream().filter(r -> r.kind() == DiffReviewPanel.DiffKind.ADDED).count();
        assertEquals(1, added);
    }

    @Test
    void detectsRemovedLine() {
        List<DiffReviewPanel.DiffRow> rows = DiffEngine.lines("a\nb\nc", "a\nb");
        long removed = rows.stream().filter(r -> r.kind() == DiffReviewPanel.DiffKind.REMOVED).count();
        assertEquals(1, removed);
    }

    @Test
    void assignsStableLineNumbers() {
        List<DiffReviewPanel.DiffRow> rows = DiffEngine.lines("x", "y");
        assertEquals(1, rows.get(0).lineNo());
        assertEquals(2, rows.get(1).lineNo());
    }

    @Test
    void truncatesVeryLargeInputs() {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < DiffEngine.MAX_LINES + 10; i++) {
            big.append("line").append(i).append('\n');
        }
        List<DiffReviewPanel.DiffRow> rows = DiffEngine.lines(big.toString(), big.toString());
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).oldLine().contains("truncated"));
    }
}
