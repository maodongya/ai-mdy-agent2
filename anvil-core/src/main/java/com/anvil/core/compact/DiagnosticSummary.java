package com.anvil.core.compact;

import com.anvil.tools.DiagnosticParser;

/** Structured diagnostic summaries for verify/compile inject (Phase 11.2). */
public final class DiagnosticSummary {

    private static final int MAX_ITEMS = 12;

    private DiagnosticSummary() {}

    public static String forOutput(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return "no diagnostic output";
        }
        var errors =
                DiagnosticParser.parse(rawOutput).stream()
                        .filter(d -> "ERROR".equalsIgnoreCase(d.severity()))
                        .toList();
        if (errors.isEmpty()) {
            return DiagnosticParser.format(DiagnosticParser.parse(rawOutput), MAX_ITEMS);
        }
        return DiagnosticParser.format(errors, MAX_ITEMS);
    }
}
