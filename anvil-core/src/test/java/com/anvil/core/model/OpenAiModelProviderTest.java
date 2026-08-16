package com.anvil.core.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiModelProviderTest {

    @Test
    void parseTextStreamInvokesDeltaCallback() throws Exception {
        String sse = """
                data: {"choices":[{"delta":{"content":"Hello"}}]}

                data: {"choices":[{"delta":{"content":" world"}}]}

                data: [DONE]

                """;
        List<String> deltas = new java.util.ArrayList<>();
        OpenAiModelProvider.StreamAggregate agg = OpenAiModelProvider.parseStream(
                new java.io.ByteArrayInputStream(sse.getBytes()), Map.of(), deltas::add);
        assertEquals("Hello world", agg.text);
        assertEquals(List.of("Hello", " world"), deltas);
    }

    @Test
    void parseTextStream() throws Exception {
        String sse = """
                data: {"choices":[{"delta":{"content":"Hello"}}]}

                data: {"choices":[{"delta":{"content":" world"}}]}

                data: [DONE]

                """;
        OpenAiModelProvider.StreamAggregate agg =
                OpenAiModelProvider.parseStream(new java.io.ByteArrayInputStream(sse.getBytes()));
        assertEquals("Hello world", agg.text);
        assertTrue(agg.toolCalls.isEmpty());
    }

    @Test
    void parseToolCallStream() throws Exception {
        String sse = """
                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"fs.read","arguments":""}}]}}]}

                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\\"path\\":\\"a.txt\\"}"}}]}}]}

                data: [DONE]

                """;
        OpenAiModelProvider.StreamAggregate agg =
                OpenAiModelProvider.parseStream(new java.io.ByteArrayInputStream(sse.getBytes()));
        assertFalse(agg.toolCalls.isEmpty());
        var call = agg.toolCalls.values().iterator().next();
        assertEquals("fs.read", call.name);
        assertEquals("a.txt", call.arguments.get("path"));
    }

    @Test
    void parseUsageFromStream() throws Exception {
        String sse = """
                data: {"choices":[{"delta":{"content":"Hi"}}]}

                data: {"choices":[],"usage":{"prompt_tokens":120,"completion_tokens":15,"prompt_tokens_details":{"cached_tokens":40}}}

                data: [DONE]

                """;
        OpenAiModelProvider.StreamAggregate agg =
                OpenAiModelProvider.parseStream(new java.io.ByteArrayInputStream(sse.getBytes()));
        assertEquals("Hi", agg.text);
        assertEquals(120, agg.usage.path("prompt_tokens").asInt());
        assertEquals(15, agg.usage.path("completion_tokens").asInt());
        assertEquals(40, agg.usage.path("prompt_tokens_details").path("cached_tokens").asInt());
    }
}
