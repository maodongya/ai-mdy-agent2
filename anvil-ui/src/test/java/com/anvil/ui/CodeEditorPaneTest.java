package com.anvil.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CodeEditorPaneTest {

    @Test
    void gutterTranslateYNegatesScrollTop() {
        assertEquals(-120, CodeEditorPane.gutterTranslateY(120), 0.001);
        assertEquals(0, CodeEditorPane.gutterTranslateY(0), 0.001);
    }
}
