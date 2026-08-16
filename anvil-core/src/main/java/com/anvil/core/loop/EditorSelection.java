package com.anvil.core.loop;

import java.util.List;

/** Editor selection injected at run start. */
public record EditorSelection(int startLine, int endLine, String text) {

    public EditorSelection {
        if (startLine < 1) {
            startLine = 1;
        }
        if (endLine < startLine) {
            endLine = startLine;
        }
        text = text == null ? "" : text.trim();
    }

    public boolean isEmpty() {
        return text.isBlank();
    }
}
