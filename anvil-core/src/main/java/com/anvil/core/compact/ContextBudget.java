package com.anvil.core.compact;

/** Tunable limits for long-context agent runs. */
public record ContextBudget(
        int compactThresholdTokens,
        int targetTokensAfterCompact,
        int keepRecentMessages,
        int maxToolContentChars) {

    public ContextBudget {
        if (compactThresholdTokens <= 0) {
            compactThresholdTokens = 40_000;
        }
        if (targetTokensAfterCompact <= 0) {
            targetTokensAfterCompact = (int) (compactThresholdTokens * 0.65);
        }
        if (keepRecentMessages <= 0) {
            keepRecentMessages = 10;
        }
        if (maxToolContentChars <= 0) {
            maxToolContentChars = 4_000;
        }
    }

    public static ContextBudget standard() {
        return new ContextBudget(40_000, 28_000, 10, 4_000);
    }

    public static ContextBudget extended() {
        return new ContextBudget(60_000, 40_000, 16, 8_000);
    }

    public static ContextBudget complex() {
        return new ContextBudget(100_000, 65_000, 24, 12_000);
    }
}
