package com.anvil.core.compact;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextCompactorTest {

    @Test
    void compactsWhenOverThreshold() {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "developer", "content", "system"));
        for (int i = 0; i < 40; i++) {
            messages.add(Map.of("role", "user", "content", "x".repeat(500)));
            messages.add(Map.of("role", "tool", "content", "y".repeat(500)));
        }

        ContextCompactor.Result result = ContextCompactor.compact(messages, 1000);
        assertTrue(result.compacted());
        assertTrue(result.afterTokens() < result.beforeTokens());
        assertTrue(result.messages().size() < messages.size());
    }

    @Test
    void skipsWhenUnderThreshold() {
        List<Map<String, Object>> messages = List.of(Map.of("role", "user", "content", "hi"));
        ContextCompactor.Result result = ContextCompactor.compact(messages, 10_000);
        assertTrue(!result.compacted());
        assertTrue(result.messages().size() == 1);
    }
}
