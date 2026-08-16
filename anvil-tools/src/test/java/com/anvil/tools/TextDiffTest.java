package com.anvil.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TextDiffTest {

    @Test
    void unifiedShowsAddedAndRemovedLines() {
        String diff = TextDiff.unified("line1\nline2\n", "line1\nline3\n", 3);

        assertTrue(diff.contains("- line2"));
        assertTrue(diff.contains("+ line3"));
    }
}
