package com.anvil.core.compact;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextCompactorSummaryTest {

    @Test
    void anchorSummarizesDroppedToolTurns() {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "developer", "content", "system"));
        for (int i = 0; i < 30; i++) {
            Map<String, Object> assistant = new LinkedHashMap<>();
            assistant.put("role", "assistant");
            assistant.put("content", "");
            assistant.put(
                    "tool_calls",
                    List.of(Map.of(
                            "id", "call_" + i,
                            "type", "function",
                            "function", Map.of("name", "fs.read", "arguments", "{}"))));
            messages.add(assistant);
            messages.add(Map.of("role", "tool", "tool_call_id", "call_" + i, "name", "fs.read", "content", "x".repeat(6000)));
        }

        ContextCompactor.Result result = ContextCompactor.compact(messages, new ContextBudget(1000, 500, 8, 2000));
        assertTrue(result.compacted());
        String anchor = result.messages().stream()
                .filter(m -> "developer".equals(m.get("role")))
                .map(m -> String.valueOf(m.get("content")))
                .filter(c -> c.contains("[compaction]"))
                .findFirst()
                .orElse("");
        assertTrue(anchor.contains("fs.read"), anchor);
    }
}
