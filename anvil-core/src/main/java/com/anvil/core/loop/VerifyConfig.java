package com.anvil.core.loop;

import com.anvil.protocol.Mode;

/** Phase 4/8: auto-verify and diagnostics after workspace writes. */
public record VerifyConfig(
        boolean autoAfterWrite,
        String commandTemplate,
        long timeoutMs,
        boolean injectFailuresIntoHistory,
        boolean autoCompileAfterWrite,
        boolean forceFixOnFailure) {

    public VerifyConfig {
        if (timeoutMs <= 0) {
            timeoutMs = 120_000L;
        }
        if (commandTemplate == null) {
            commandTemplate = "";
        }
    }

    /** Backward-compatible ctor without Phase 8 fields. */
    public VerifyConfig(
            boolean autoAfterWrite,
            String commandTemplate,
            long timeoutMs,
            boolean injectFailuresIntoHistory) {
        this(autoAfterWrite, commandTemplate, timeoutMs, injectFailuresIntoHistory, true, true);
    }

    public static VerifyConfig disabled() {
        return new VerifyConfig(false, "", 120_000L, true, false, true);
    }

    public static VerifyConfig defaults() {
        return new VerifyConfig(true, "", 90_000L, true, true, true);
    }

    /** Profile-aware verify defaults (Phase 8.1). */
    public static VerifyConfig forRun(VerifyConfig base, Mode mode, RunProfile profile) {
        if (base == null) {
            return disabled();
        }
        boolean autoVerify = base.autoAfterWrite() || profileDefaultAutoVerify(mode, profile);
        boolean autoCompile = base.autoCompileAfterWrite();
        return new VerifyConfig(
                autoVerify,
                base.commandTemplate(),
                base.timeoutMs(),
                base.injectFailuresIntoHistory(),
                autoCompile,
                base.forceFixOnFailure());
    }

    /** Agent + extended/complex profiles default to auto-verify; standard stays off unless yaml enables. */
    static boolean profileDefaultAutoVerify(Mode mode, RunProfile profile) {
        if (mode != Mode.AGENT && mode != Mode.DEBUG) {
            return false;
        }
        return profile == RunProfile.EXTENDED || profile == RunProfile.COMPLEX;
    }
}
