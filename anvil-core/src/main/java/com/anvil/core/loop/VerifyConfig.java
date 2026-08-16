package com.anvil.core.loop;

/** Phase 4: auto-verify after workspace writes. */
public record VerifyConfig(
        boolean autoAfterWrite,
        String commandTemplate,
        long timeoutMs,
        boolean injectFailuresIntoHistory) {

    public VerifyConfig {
        if (timeoutMs <= 0) {
            timeoutMs = 120_000L;
        }
        if (commandTemplate == null) {
            commandTemplate = "";
        }
    }

    public static VerifyConfig disabled() {
        return new VerifyConfig(false, "", 120_000L, true);
    }

    public static VerifyConfig defaults() {
        return new VerifyConfig(true, "", 180_000L, true);
    }
}
