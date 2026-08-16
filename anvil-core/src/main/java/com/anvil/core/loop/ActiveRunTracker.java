package com.anvil.core.loop;

import java.util.concurrent.ConcurrentHashMap;

/** Tracks in-flight runs so {@link RunService} cancel requests reach the loop. */
public final class ActiveRunTracker {

    private static final ConcurrentHashMap<String, RunContext> ACTIVE = new ConcurrentHashMap<>();

    private ActiveRunTracker() {}

    public static void track(RunContext ctx) {
        if (ctx != null) {
            ACTIVE.put(ctx.runId(), ctx);
        }
    }

    public static void untrack(String runId) {
        if (runId != null) {
            ACTIVE.remove(runId);
        }
    }

    public static boolean cancel(String runId) {
        RunContext ctx = ACTIVE.get(runId);
        if (ctx == null) {
            return false;
        }
        ctx.cancel();
        return true;
    }

    /** Visible for tests. */
    static int activeCount() {
        return ACTIVE.size();
    }
}
