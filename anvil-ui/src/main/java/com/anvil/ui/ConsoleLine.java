package com.anvil.ui;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** A single line in the agent console. */
record ConsoleLine(Kind kind, String text, LocalTime at) {
    enum Kind {
        USER,
        MESSAGE,
        TOOL,
        APPROVAL,
        ERROR,
        SYSTEM,
        CONTEXT,
        METRICS
    }

    static ConsoleLine of(Kind kind, String text) {
        return new ConsoleLine(kind, text, LocalTime.now());
    }

    String formattedTime() {
        return at.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
}
