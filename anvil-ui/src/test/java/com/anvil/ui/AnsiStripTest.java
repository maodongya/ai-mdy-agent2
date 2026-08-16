package com.anvil.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnsiStripTest {

    @Test
    void stripsColorCodes() {
        assertEquals("hello world", AnsiStrip.plain("\u001B[31mhello\u001B[0m world"));
    }

    @Test
    void leavesPlainText() {
        assertEquals("ok", AnsiStrip.plain("ok"));
    }
}
