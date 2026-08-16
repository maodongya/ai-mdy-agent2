package com.anvil.core.compact;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageHistorySanitizerTest {

    @Test
    void dropsOrphanToolAtStart() {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "tool", "tool_call_id", "call_1", "content", "output"),
                Map.of("role", "user", "content", "hi"));

        List<Map<String, Object>> sanitized = MessageHistorySanitizer.sanitize(messages);
        assertEquals(1, sanitized.size());
        assertEquals("user", sanitized.get(0).get("role"));
    }

    @Test
    void keepsToolAfterAssistantToolCalls() {
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("role", "assistant");
        assistant.put("content", "");
        assistant.put(
                "tool_calls",
                List.of(Map.of("id", "call_1", "type", "function", "function", Map.of("name", "fs.read", "arguments", "{}"))));

        List<Map<String, Object>> messages = List.of(
                assistant,
                Map.of("role", "tool", "tool_call_id", "call_1", "content", "file body"),
                Map.of("role", "user", "content", "next"));

        List<Map<String, Object>> sanitized = MessageHistorySanitizer.sanitize(messages);
        assertEquals(3, sanitized.size());
    }

    @Test
    void alignTailStartIncludesAssistantForLeadingTool() {
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("role", "assistant");
        assistant.put("content", "");
        assistant.put(
                "tool_calls",
                List.of(Map.of("id", "call_9", "type", "function", "function", Map.of("name", "fs.read", "arguments", "{}"))));

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "old"));
        messages.add(assistant);
        messages.add(Map.of("role", "tool", "tool_call_id", "call_9", "content", "data"));
        messages.add(Map.of("role", "user", "content", "new"));

        int start = MessageHistorySanitizer.alignTailStart(messages, 2);
        assertEquals(1, start);
        assertTrue(messages.get(start).containsKey("tool_calls"));
    }

    @Test
    void dropsAssistantToolCallsWithoutAllToolResponses() {
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("role", "assistant");
        assistant.put("content", "");
        assistant.put(
                "tool_calls",
                List.of(
                        Map.of("id", "call_1", "type", "function", "function", Map.of("name", "fs.read", "arguments", "{}")),
                        Map.of("id", "call_2", "type", "function", "function", Map.of("name", "grep", "arguments", "{}"))));

        List<Map<String, Object>> messages = List.of(
                assistant,
                Map.of("role", "tool", "tool_call_id", "call_1", "content", "only one"),
                Map.of("role", "user", "content", "next"));

        List<Map<String, Object>> sanitized = MessageHistorySanitizer.sanitize(messages);
        assertEquals(1, sanitized.size());
        assertEquals("user", sanitized.get(0).get("role"));
    }

    @Test
    void compactionDoesNotLeaveOrphanTool() {
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("role", "assistant");
        assistant.put("content", "");
        assistant.put(
                "tool_calls",
                List.of(Map.of("id", "call_x", "type", "function", "function", Map.of("name", "fs.read", "arguments", "{}"))));

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "developer", "content", "sys"));
        messages.add(Map.of("role", "user", "content", "a".repeat(500)));
        messages.add(assistant);
        messages.add(Map.of("role", "tool", "tool_call_id", "call_x", "content", "b".repeat(500)));
        for (int i = 0; i < 30; i++) {
            messages.add(Map.of("role", "user", "content", "x".repeat(400)));
        }

        ContextCompactor.Result result = ContextCompactor.compact(messages, new ContextBudget(1000, 500, 4, 2000));
        assertTrue(result.compacted());
        List<Map<String, Object>> out = result.messages();
        for (int i = 0; i < out.size(); i++) {
            if ("tool".equals(out.get(i).get("role"))) {
                assertTrue(i > 0, "tool at index 0");
                assertEquals("assistant", out.get(i - 1).get("role"));
            }
        }
    }
}
