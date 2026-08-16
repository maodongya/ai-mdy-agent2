package com.anvil.core.compact;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Heuristic context compaction with summaries for long / complex runs. */
public final class ContextCompactor {

    private static final Set<String> WRITE_TOOLS =
            Set.of("fs.write", "search_replace", "apply_patch", "edit.plan");

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
            String toolName = String.valueOf(msg.getOrDefault("name", ""));
            copy.put(
                    "content",
                    ToolContentBudget.apply(
                            String.valueOf(msg.getOrDefault("content", "")), toolName, budget.maxToolContentChars()));
            out.add(copy);
        }
        return out;
    }

    /** Phase 11.5: cross-run thread memory trim — summary + recent tail. */
    public static List<Map<String, Object>> trimForThreadMemory(List<Map<String, Object>> messages, int keepRecent) {
        if (messages == null || messages.isEmpty() || messages.size() <= keepRecent) {
            return messages == null ? List.of() : List.copyOf(messages);
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
        if (rest.size() <= keepRecent) {
            return List.copyOf(messages);
        }
        int start = rest.size() - keepRecent;
        start = MessageHistorySanitizer.alignTailStart(rest, start);
        List<Map<String, Object>> dropped = rest.subList(0, start);
        List<Map<String, Object>> recent = rest.subList(start, rest.size());
        List<Map<String, Object>> out = new ArrayList<>(pinned);
        out.add(Map.of("role", "developer", "content", summarizeDropped(dropped, null)));
        out.addAll(recent);
        return List.copyOf(out);
    }

    private static String summarizeDropped(List<Map<String, Object>> dropped, RunAnchors anchors) {
        if (dropped.isEmpty()) {
            return anchorPrefix(anchors) + "[compaction] Context trimmed to fit budget.";
        }
        Set<String> filesRead = new LinkedHashSet<>();
        Set<String> filesChanged = new LinkedHashSet<>();
        List<String> failures = new ArrayList<>();
        List<String> bullets = new ArrayList<>();

        for (Map<String, Object> msg : dropped) {
            String role = String.valueOf(msg.getOrDefault("role", "?"));
            switch (role) {
                case "tool" -> {
                    String name = String.valueOf(msg.getOrDefault("name", "?"));
                    String content = String.valueOf(msg.getOrDefault("content", ""));
                    if ("fs.read".equals(name)) {
                        extractPaths(content).forEach(filesRead::add);
                    }
                    if (WRITE_TOOLS.contains(name)) {
                        extractPaths(content).forEach(filesChanged::add);
                    }
                    if ("error".equals(String.valueOf(msg.getOrDefault("status", "")))) {
                        failures.add(name + ": " + firstLine(content, 80));
                    }
                    if (bullets.size() < 16) {
                        bullets.add("- tool " + name + ": " + firstLine(content, 80));
                    }
                }
                case "user" -> {
                    if (bullets.size() < 16) {
                        bullets.add("- user: " + firstLine(String.valueOf(msg.getOrDefault("content", "")), 80));
                    }
                }
                case "assistant" -> {
                    if (bullets.size() < 16) {
                        bullets.add("- assistant" + (msg.containsKey("tool_calls") ? " (tool calls)" : ""));
                    }
                }
                default -> {
                    if (bullets.size() < 16) {
                        bullets.add("- " + role);
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(anchorPrefix(anchors));
        sb.append("[compaction] Summarized ").append(dropped.size()).append(" earlier turns.\n");
        if (!filesRead.isEmpty()) {
            sb.append("files_read: ").append(String.join(", ", filesRead.stream().limit(12).toList())).append('\n');
        }
        if (!filesChanged.isEmpty()) {
            sb.append("files_changed: ")
                    .append(String.join(", ", filesChanged.stream().limit(12).toList()))
                    .append('\n');
        }
        if (!failures.isEmpty()) {
            sb.append("failures: ").append(String.join("; ", failures.stream().limit(6).toList())).append('\n');
        }
        for (String bullet : bullets) {
            sb.append(bullet).append('\n');
        }
        if (dropped.size() > bullets.size()) {
            sb.append("- … ").append(dropped.size() - bullets.size()).append(" more omitted\n");
        }
        sb.append("Continue from recent turns below. Do not re-fetch files already summarized unless needed.");
        return sb.toString().trim();
    }

    private static Set<String> extractPaths(String content) {
        Set<String> paths = new LinkedHashSet<>();
        if (content == null || content.isBlank()) {
            return paths;
        }
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.endsWith(".java") || trimmed.endsWith(".xml") || trimmed.endsWith(".md")) {
                int idx = trimmed.indexOf(':');
                paths.add(idx > 0 ? trimmed.substring(0, idx).trim() : trimmed);
            }
        }
        return paths;
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
