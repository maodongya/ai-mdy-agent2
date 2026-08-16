package com.anvil.core.orchestrator;

import com.anvil.core.loop.LoopConfig;
import com.anvil.core.loop.RunProfile;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Decide whether explore sub-agent runs (Phase 11.4). */
public final class ExploreGate {

    private static final Pattern MULTI_FILE =
            Pattern.compile("(?i)(refactor|across|multiple|several|模块|跨|多个|重构)");

    private ExploreGate() {}

    public static boolean shouldRun(RunProfile profile, LoopConfig config, String userMessage) {
        if (config == null || !config.exploreSubAgent()) {
            return false;
        }
        if (profile == RunProfile.COMPLEX) {
            return true;
        }
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String msg = userMessage.toLowerCase(Locale.ROOT);
        if (MULTI_FILE.matcher(msg).find()) {
            return true;
        }
        for (String kw : Set.of("architecture", "codebase", "whole project", "全库", "整个项目")) {
            if (msg.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
