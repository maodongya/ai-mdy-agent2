package com.anvil.ui;

import com.anvil.protocol.ProtocolJson;

import java.util.Map;

/** Formats agent SSE events for the workbench console. */
final class AgentEventFormatter {

    private AgentEventFormatter() {}

    static String format(String type, Map<String, Object> payload) {
        return switch (type) {
            case "run.started" -> "run started · mode="
                    + str(payload.get("mode"))
                    + " model="
                    + str(payload.get("model"))
                    + " profile="
                    + str(payload.get("profile"))
                    + " steps="
                    + num(payload.get("max_steps"))
                    + " budget="
                    + num(payload.get("context_budget"));
            case "thread.memory.loaded" -> "memory loaded · "
                    + num(payload.get("messages"))
                    + " msgs · ~"
                    + num(payload.get("tokens_estimate"))
                    + " tok";
            case "step.started" -> "step "
                    + num(payload.get("step"))
                    + " · context "
                    + num(payload.get("context_messages"))
                    + " msgs · ~"
                    + num(payload.get("context_tokens_estimate"))
                    + " tok · "
                    + num(payload.get("tools_available"))
                    + " tools";
            case "model.completed" -> formatModelCompleted(payload);
            case "context.compacted" -> "context compacted · "
                    + num(payload.get("before_tokens"))
                    + " → "
                    + num(payload.get("after_tokens"))
                    + " tok";
            case "message.delta" -> str(payload.get("delta"), "");
            case "message.completed" -> str(payload.get("text"), "(empty)");
            case "tool.planned" -> "plan · "
                    + str(payload.get("name"))
                    + argsSummary(payload.get("arguments"));
            case "tool.started" -> "exec · " + str(payload.get("name"), str(payload.get("tool_call_id")));
            case "tool.completed" -> "done · "
                    + str(payload.get("tool_call_id"))
                    + preview(payload.get("preview"))
                    + flag(payload.get("truncated"), " [truncated]");
            case "tool.failed" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> err = (Map<String, Object>) payload.getOrDefault("error", Map.of());
                yield "failed · " + str(err.get("message"), "tool error");
            }
            case "edit.summary" -> "edit · "
                    + str(payload.get("path"))
                    + " +"
                    + num(payload.get("lines_added"))
                    + " -"
                    + num(payload.get("lines_removed"));
            case "edit.preview" -> "diff · " + str(payload.get("path"));
            case "tools.parallel.started" -> "parallel · " + num(payload.get("count")) + " read tools";
            case "tools.parallel.completed" -> "parallel done · " + num(payload.get("count")) + " tools";
            case "writes.parallel.started" -> "parallel writes · " + num(payload.get("count"));
            case "writes.parallel.completed" -> "parallel writes done · " + num(payload.get("count"));
            case "explore.started" -> "explore · max " + num(payload.get("max_steps")) + " steps";
            case "explore.completed" -> "explore done · "
                    + num(payload.get("files"))
                    + " files · "
                    + num(payload.get("tool_calls"))
                    + " tools";
            case "planner.required" -> "planner · call plan.update → " + str(payload.get("plan_path"));
            case "planner.completed" -> "planner ok · " + num(payload.get("steps")) + " steps";
            case "verify.started" -> "verify · " + str(payload.get("command"));
            case "verify.completed" -> "verify ok · " + str(payload.get("command"));
            case "verify.failed" -> "verify failed · "
                    + str(payload.get("command"))
                    + preview(payload.get("preview"));
            case "diagnostics.auto.started" -> "compile · " + str(payload.get("path"));
            case "diagnostics.auto.completed" -> "compile ok · " + str(payload.get("path"));
            case "diagnostics.auto.failed" -> "compile failed · "
                    + str(payload.get("path"))
                    + preview(payload.get("preview"));
            case "approval.required" -> "approval · "
                    + str(payload.get("tool"))
                    + " · risk="
                    + str(payload.get("risk"));
            case "approval.resolved" -> "approval "
                    + str(payload.get("approval_id"))
                    + " → "
                    + str(payload.get("decision"));
            case "model.routed" -> "model routed · step "
                    + num(payload.get("step"))
                    + " → "
                    + str(payload.get("model"));
            case "run.completed" -> formatRunCompleted(payload);
            case "run.failed" -> formatRunFailed(payload);
            case "run.cancelled" -> type + usageSuffix(payload.get("usage"));
            default -> type;
        };
    }

    /** Verbose telemetry goes to Trace tab only — keeps Console ListView light during long runs. */
    static boolean showInConsole(String type) {
        return switch (type) {
            case "step.started", "model.completed", "context.compacted", "thread.memory.loaded" -> false;
            default -> true;
        };
    }

    static ConsoleLine.Kind kindFor(String type) {
        return switch (type) {
            case "message.completed", "message.delta" -> ConsoleLine.Kind.MESSAGE;
            case "tool.planned", "tool.started", "tool.completed", "edit.summary", "edit.preview" -> ConsoleLine.Kind.TOOL;
            case "tools.parallel.started", "tools.parallel.completed", "writes.parallel.started", "writes.parallel.completed",
                    "explore.started", "explore.completed", "planner.required", "planner.completed" -> ConsoleLine.Kind.CONTEXT;
            case "verify.started", "verify.completed", "verify.failed",
                    "diagnostics.auto.started", "diagnostics.auto.completed", "diagnostics.auto.failed" -> ConsoleLine.Kind.TOOL;
            case "approval.required", "approval.resolved" -> ConsoleLine.Kind.APPROVAL;
            case "run.failed", "run.cancelled", "tool.failed" -> ConsoleLine.Kind.ERROR;
            case "step.started", "context.compacted", "thread.memory.loaded" -> ConsoleLine.Kind.CONTEXT;
            case "model.completed", "run.completed" -> ConsoleLine.Kind.METRICS;
            default -> ConsoleLine.Kind.SYSTEM;
        };
    }

    static String metricsSummary(Map<String, Object> usage) {
        if (usage == null || usage.isEmpty()) {
            return "";
        }
        return "Σ in="
                + num(usage.get("input_tokens"))
                + " out="
                + num(usage.get("output_tokens"))
                + " tools="
                + num(usage.get("tool_calls"))
                + cacheSuffix(usage);
    }

    private static String formatModelCompleted(Map<String, Object> payload) {
        StringBuilder sb = new StringBuilder();
        sb.append("model · step=").append(num(payload.get("step")));
        sb.append(" kind=").append(str(payload.get("kind")));
        if (payload.containsKey("input_tokens")) {
            sb.append(" · in=").append(num(payload.get("input_tokens")));
            sb.append(" out=").append(num(payload.get("output_tokens")));
            if (payload.get("cached_tokens") != null) {
                sb.append(" cached=").append(num(payload.get("cached_tokens")));
                Object in = payload.get("input_tokens");
                if (in instanceof Number n && n.longValue() > 0 && payload.get("cached_tokens") instanceof Number c) {
                    int pct = (int) Math.round(100.0 * c.longValue() / n.longValue());
                    sb.append(" (").append(pct).append("% cache)");
                }
            }
            sb.append(" · ").append(num(payload.get("latency_ms"))).append("ms");
        }
        Object total = payload.get("usage_total");
        if (total instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            String summary = metricsSummary((Map<String, Object>) map);
            if (!summary.isBlank()) {
                sb.append(" · ").append(summary);
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String formatRunCompleted(Map<String, Object> payload) {
        return "run completed · status="
                + str(payload.get("status"))
                + usageSuffix(payload.get("usage"));
    }

    @SuppressWarnings("unchecked")
    private static String formatRunFailed(Map<String, Object> payload) {
        Map<String, Object> err = (Map<String, Object>) payload.getOrDefault("error", Map.of());
        return "run failed · " + str(err.get("message"), "unknown error") + usageSuffix(payload.get("usage"));
    }

    private static String cacheSuffix(Map<String, Object> usage) {
        Object ratio = usage.get("cache_hit_ratio");
        if (ratio instanceof Number n) {
            return " cache=" + Math.round(n.doubleValue() * 100) + "%";
        }
        return "";
    }

    private static String usageSuffix(Object usageObj) {
        if (!(usageObj instanceof Map<?, ?> map)) {
            return "";
        }
        @SuppressWarnings("unchecked")
        String summary = metricsSummary((Map<String, Object>) map);
        return summary.isBlank() ? "" : " · " + summary;
    }

    private static String argsSummary(Object argsObj) {
        if (argsObj == null) {
            return "";
        }
        try {
            String json = argsObj instanceof String s ? s : ProtocolJson.toJson(argsObj);
            return json.length() <= 120 ? " " + json : " " + json.substring(0, 117) + "...";
        } catch (Exception e) {
            return "";
        }
    }

    private static String preview(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return "";
        }
        return " · " + value;
    }

    private static String flag(Object value, String label) {
        return Boolean.TRUE.equals(value) ? label : "";
    }

    private static String str(Object value) {
        return value == null ? "?" : String.valueOf(value);
    }

    private static String str(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static String num(Object value) {
        return value == null ? "0" : String.valueOf(value);
    }
}
