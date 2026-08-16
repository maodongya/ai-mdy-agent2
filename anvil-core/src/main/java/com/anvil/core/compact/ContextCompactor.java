package com.anvil.core.compact;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Heuristic context compaction with summaries for long / complex runs. */
public final class ContextCompactor {

    private ContextCompactor() {}

    public record Result(List<Map<String, Object>> messages, int beforeTokens, int afterTokens, boolean compacted) {}

    public static Result compact(List<Map<String, Object>> messages, int thresholdTokens) {
        return compact(messages, new ContextBudget(thresholdTokens, 0, 0, 0));
    }

    public static Result compact(List<Map<String, Object>> messages, ContextBudget budget) {
        return compact(messages, budget, null);
    }

    public static Result compact(List<Map<String, Object>> messages, ContextBudget budget, RunAnchors anchors) {
        if (messages == null || messages.isEmpty()) {
            return new Result(List.of(), 0, 0, false);
        }
        ContextBudget effective = budget == null ? ContextBudget.standard() : budget;
        int before = estimateTokens(messages);
        if (before <= effective.compactThresholdTokens()) {
            return new Result(MessageHistorySanitizer.sanitize(truncateToolContents(List.copyOf(messages), effective)), before, before, false);
        }

        List<Map<String, Object>> pinned = new ArrayList<>();
        List<Map<String, Object>> rest = new ArrayList<>();
        for (Map<String, Object> msg : messages) {
            String role = String.valueOf(msg.getOrDefault("role", ""));
            if ("developer".equals(role) || "system".equals(role)) {
                pinned.add(msg);
            } else {
                rest.add(msg);
            }
        }

        int keep = Math.min(effective.keepRecentMessages(), rest.size());
        int start = keep == rest.size() ? 0 : rest.size() - keep;
        start = MessageHistorySanitizer.alignTailStart(rest, start);
        List<Map<String, Object>> recent = keep == rest.size() ? rest : rest.subList(start, rest.size());
        List<Map<String, Object>> dropped = keep == rest.size() ? List.of() : rest.subList(0, start);

        Map<String, Object> anchor = new LinkedHashMap<>();
        anchor.put("role", "developer");
        anchor.put("content", summarizeDropped(dropped, anchors));

        List<Map<String, Object>> compacted = new ArrayList<>(pinned);
        compacted.add(anchor);
        compacted.addAll(recent);
        compacted = truncateToolContents(compacted, effective);
        compacted = MessageHistorySanitizer.sanitize(compacted);

        int after = estimateTokens(compacted);
        if (after > effective.targetTokensAfterCompact() && keep > 4) {
            ContextBudget tighter = new ContextBudget(
                    effective.compactThresholdTokens(),
                    effective.targetTokensAfterCompact(),
                    Math.max(4, keep / 2),
                    effective.maxToolContentChars());
            return compact(compacted, tighter, anchors);
        }

        return new Result(List.copyOf(compacted), before, after, true);
    }

    public static String truncateContent(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return content == null ? "" : content;
        }
        return content.substring(0, maxChars) + "\n… [truncated " + (content.length() - maxChars) + " chars]";
    }

    public static int estimateTokens(List<Map<String, Object>> messages) {
        int chars = 0;
        for (Map<String, Object> msg : messages) {
            chars += String.valueOf(msg.getOrDefault("content", "")).length();
            chars += String.valueOf(msg.getOrDefault("name", "")).length();
            Object toolCalls = msg.get("tool_calls");
            if (toolCalls != null) {
                chars += String.valueOf(toolCalls).length();
            }
        }
        return Math.max(1, chars / 4);
    }

    private static List<Map<String, Object>> truncateToolContents(
            List<Map<String, Object>> messages, ContextBudget budget) {
        List<Map<String, Object>> out = new ArrayList<>(messages.size());
        for (Map<String, Object> msg : messages) {
            if (!"tool".equals(String.valueOf(msg.getOrDefault("role", "")))) {
                out.add(msg);
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>(msg);
            copy.put(
                    "content",
                    truncateContent(String.valueOf(msg.getOrDefault("content", "")), budget.maxToolContentChars()));
            out.add(copy);
        }
        return out;
    }

    private static String summarizeDropped(List<Map<String, Object>> dropped, RunAnchors anchors) {
        if (dropped.isEmpty()) {
            return anchorPrefix(anchors) + "[compaction] Context trimmed to fit budget.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(anchorPrefix(anchors));
        sb.append("[compaction] Summarized ").append(dropped.size()).append(" earlier turns:\n");
        int lines = 0;
        for (Map<String, Object> msg : dropped) {
            if (lines >= 24) {
                sb.append("- … ").append(dropped.size() - lines).append(" more omitted\n");
                break;
            }
            String role = String.valueOf(msg.getOrDefault("role", "?"));
            switch (role) {
                case "tool" -> sb.append("- tool ")
                        .append(msg.getOrDefault("name", "?"))
                        .append(": ")
                        .append(firstLine(String.valueOf(msg.getOrDefault("content", "")), 100))
                        .append('\n');
                case "user" -> sb.append("- user: ")
                        .append(firstLine(String.valueOf(msg.getOrDefault("content", "")), 100))
                        .append('\n');
                case "assistant" -> sb.append("- assistant")
                        .append(msg.containsKey("tool_calls") ? " (tool calls)" : "")
                        .append('\n');
                default -> sb.append("- ").append(role).append('\n');
            }
            lines++;
        }
        sb.append("Continue from recent turns below. Do not re-fetch files already summarized unless needed.");
        return sb.toString().trim();
    }

    private static String anchorPrefix(RunAnchors anchors) {
        if (anchors == null || anchors.isEmpty()) {
            return "";
        }
        return anchors.formatBlock() + "\n\n";
    }

    private static String firstLine(String text, int max) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String line = text.split("\n", 2)[0].trim();
        return line.length() <= max ? line : line.substring(0, max - 3) + "...";
    }
}
