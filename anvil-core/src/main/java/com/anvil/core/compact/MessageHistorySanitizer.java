package com.anvil.core.compact;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ensures OpenAI/DeepSeek-compatible message sequences: every {@code role=tool} message must follow
 * an {@code assistant} message with matching {@code tool_calls}.
 */
public final class MessageHistorySanitizer {

    private MessageHistorySanitizer() {}

    /** Drop orphan tool messages and align slice boundaries for storage / compaction. */
    public static List<Map<String, Object>> sanitize(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>(messages.size());
        for (Map<String, Object> msg : messages) {
            if (isTool(msg)) {
                String toolCallId = toolCallId(msg);
                if (toolCallId != null && hasPrecedingAssistantToolCall(out, toolCallId)) {
                    out.add(msg);
                }
                continue;
            }
            out.add(msg);
        }
        return dropIncompleteToolTurns(out);
    }

    /**
     * Removes assistant {@code tool_calls} turns that do not have a tool response for every call id.
     * Prevents DeepSeek/OpenAI 400 errors when replaying corrupted thread memory.
     */
    public static List<Map<String, Object>> dropIncompleteToolTurns(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>(messages.size());
        int i = 0;
        while (i < messages.size()) {
            Map<String, Object> msg = messages.get(i);
            if (isAssistantWithToolCalls(msg)) {
                Set<String> required = toolCallIds(msg);
                int j = i + 1;
                Set<String> answered = new HashSet<>();
                while (j < messages.size() && isTool(messages.get(j))) {
                    String id = toolCallId(messages.get(j));
                    if (id != null) {
                        answered.add(id);
                    }
                    j++;
                }
                if (answered.containsAll(required)) {
                    for (int k = i; k < j; k++) {
                        out.add(messages.get(k));
                    }
                    i = j;
                    continue;
                }
                Map<String, Object> stripped = new LinkedHashMap<>(msg);
                stripped.remove("tool_calls");
                String content = String.valueOf(stripped.getOrDefault("content", ""));
                if (!content.isBlank()) {
                    out.add(stripped);
                }
                i = j;
                continue;
            }
            out.add(msg);
            i++;
        }
        return List.copyOf(out);
    }

    /**
     * When taking the tail of history (compaction or SQLite cap), extend {@code start} backward so
     * the slice does not begin with tool messages whose assistant turn was dropped.
     */
    public static int alignTailStart(List<Map<String, Object>> messages, int start) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int aligned = Math.max(0, Math.min(start, messages.size()));
        aligned = skipLeadingOrphanTools(messages, aligned);
        aligned = includeAssistantForLeadingTools(messages, aligned);
        return Math.max(0, Math.min(aligned, messages.size()));
    }

    private static int skipLeadingOrphanTools(List<Map<String, Object>> messages, int start) {
        int idx = start;
        while (idx < messages.size() && isTool(messages.get(idx))) {
            String id = toolCallId(messages.get(idx));
            if (id != null && findAssistantWithToolCall(messages, idx - 1, id) >= 0) {
                break;
            }
            idx++;
        }
        return idx;
    }

    private static int includeAssistantForLeadingTools(List<Map<String, Object>> messages, int start) {
        int idx = start;
        while (idx > 0 && idx < messages.size() && isTool(messages.get(idx))) {
            String id = toolCallId(messages.get(idx));
            if (id == null) {
                idx++;
                break;
            }
            int assistantIdx = findAssistantWithToolCall(messages, idx - 1, id);
            if (assistantIdx < 0) {
                idx++;
                break;
            }
            if (assistantIdx < idx) {
                idx = assistantIdx;
                break;
            }
            break;
        }
        return idx;
    }

    private static int findAssistantWithToolCall(List<Map<String, Object>> messages, int fromIndex, String toolCallId) {
        for (int i = fromIndex; i >= 0; i--) {
            Map<String, Object> msg = messages.get(i);
            if (isTool(msg)) {
                continue;
            }
            if (isAssistantWithToolCalls(msg) && assistantHasToolCallId(msg, toolCallId)) {
                return i;
            }
            return -1;
        }
        return -1;
    }

    private static boolean hasPrecedingAssistantToolCall(List<Map<String, Object>> out, String toolCallId) {
        for (int i = out.size() - 1; i >= 0; i--) {
            Map<String, Object> msg = out.get(i);
            if (isTool(msg)) {
                continue;
            }
            if (isAssistantWithToolCalls(msg)) {
                return assistantHasToolCallId(msg, toolCallId);
            }
            return false;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> toolCallIds(Map<String, Object> assistant) {
        Object raw = assistant.get("tool_calls");
        if (!(raw instanceof List<?> list)) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> tc) {
                Object id = tc.get("id");
                if (id != null && !String.valueOf(id).isBlank()) {
                    ids.add(String.valueOf(id));
                }
            }
        }
        return ids;
    }

    @SuppressWarnings("unchecked")
    private static boolean assistantHasToolCallId(Map<String, Object> assistant, String toolCallId) {
        Object raw = assistant.get("tool_calls");
        if (!(raw instanceof List<?> list)) {
            return false;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> tc) {
                Object id = tc.get("id");
                if (id != null && toolCallId.equals(String.valueOf(id))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isTool(Map<String, Object> msg) {
        return "tool".equals(String.valueOf(msg.getOrDefault("role", "")));
    }

    private static boolean isAssistantWithToolCalls(Map<String, Object> msg) {
        return "assistant".equals(String.valueOf(msg.getOrDefault("role", ""))) && msg.containsKey("tool_calls");
    }

    private static String toolCallId(Map<String, Object> msg) {
        Object id = msg.get("tool_call_id");
        if (id == null) {
            return null;
        }
        String s = String.valueOf(id);
        return s.isBlank() ? null : s;
    }
}
