package com.anvil.core.compact;

/** Tunable limits for long-context agent runs. */
public record ContextBudget(
        int compactThresholdTokens,
        int targetTokensAfterCompact,
        int keepRecentMessages,
        int maxToolContentChars) {

    public ContextBudget {
        if (compactThresholdTokens <= 0) {
            compactThresholdTokens = 120_000;
        }
        if (targetTokensAfterCompact <= 0) {
            targetTokensAfterCompact = (int) (compactThresholdTokens * 0.65);
        }
        if (keepRecentMessages <= 0) {
            keepRecentMessages = 12;
        }
        if (maxToolContentChars <= 0) {
            maxToolContentChars = 8_000;
        }
    }

    public static ContextBudget standard() {
        return new ContextBudget(120_000, 80_000, 12, 8_000);
    }

    public static ContextBudget extended() {
        return new ContextBudget(200_000, 130_000, 24, 16_000);
    }

    public static ContextBudget complex() {
        return new ContextBudget(280_000, 180_000, 32, 24_000);
    }
}
