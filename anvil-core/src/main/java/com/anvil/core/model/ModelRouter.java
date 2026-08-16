package com.anvil.core.model;

import com.anvil.core.loop.RunProfile;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Per-step model routing: explore=mini, edit=chat, plan=reasoner (Phase 9.4). */
public final class ModelRouter {

    private static final Set<String> WRITE_TOOLS = Set.of(
            "fs.write", "fs.apply_patch", "search_replace", "apply_patch", "edit.plan");
    private static final Set<String> PLAN_TOOLS = Set.of("plan.update", "edit.plan");

    private ModelRouter() {}

    public enum StepKind {
        EXPLORE,
        EDIT,
        PLAN
    }

    public static StepKind classify(List<Map<String, Object>> history, RunProfile profile, int step) {
        if (profile == RunProfile.COMPLEX && step <= 2) {
            return StepKind.PLAN;
        }
        if (lastAssistantUsedTool(history, PLAN_TOOLS)) {
            return StepKind.PLAN;
        }
        if (lastAssistantUsedTool(history, WRITE_TOOLS)) {
            return StepKind.EDIT;
        }
        if (recentWriteInHistory(history, 4)) {
            return StepKind.EDIT;
        }
        return StepKind.EXPLORE;
    }

    public static String route(String baseModel, StepKind kind, ModelRoutingConfig config) {
        if (config == null || !config.enabled()) {
            return baseModel;
        }
        return switch (kind) {
            case EXPLORE -> pick(config.exploreModel(), baseModel);
            case EDIT -> pick(config.editModel(), baseModel);
            case PLAN -> pick(config.planModel(), baseModel);
        };
    }

    private static String pick(String configured, String fallback) {
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        return configured.trim();
    }

    private static boolean lastAssistantUsedTool(List<Map<String, Object>> history, Set<String> tools) {
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, Object> msg = history.get(i);
            if (!"assistant".equals(msg.get("role"))) {
                continue;
            }
            Object toolCalls = msg.get("tool_calls");
            if (!(toolCalls instanceof List<?> list)) {
                return false;
            }
            for (Object tc : list) {
                if (tc instanceof Map<?, ?> map) {
                    Object fn = map.get("function");
                    if (fn instanceof Map<?, ?> fnMap) {
                        String name = String.valueOf(fnMap.get("name"));
                        if (tools.contains(name)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
        return false;
    }

    private static boolean recentWriteInHistory(List<Map<String, Object>> history, int lookback) {
        int seen = 0;
        for (int i = history.size() - 1; i >= 0 && seen < lookback; i--) {
            Map<String, Object> msg = history.get(i);
            if ("tool".equals(msg.get("role"))) {
                seen++;
                String name = String.valueOf(msg.get("name"));
                if (WRITE_TOOLS.contains(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String stepKindWire(StepKind kind) {
        return kind.name().toLowerCase(Locale.ROOT);
    }
}
