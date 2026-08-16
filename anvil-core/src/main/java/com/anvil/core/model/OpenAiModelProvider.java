package com.anvil.core.model;

import com.anvil.core.prompt.PromptBundle;
import com.anvil.core.tools.OpenAiToolFormat;
import com.anvil.core.compact.MessageHistorySanitizer;
import com.anvil.protocol.ProtocolJson;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** OpenAI Chat Completions with streaming SSE aggregation. */
public final class OpenAiModelProvider implements ModelProvider {

    private final OpenAiConfig config;
    private final HttpClient httpClient;

    public OpenAiModelProvider(OpenAiConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.timeout()).build());
    }

    OpenAiModelProvider(OpenAiConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    @Override
    public Optional<ModelTurn> nextTurn(ModelTurnContext context) {
        if (!config.isConfigured()) {
            throw new IllegalStateException(config.providerLabel() + " API key not configured");
        }
        PromptBundle prompt = context.prompt();
        if (prompt == null) {
            throw new IllegalArgumentException("prompt bundle required for OpenAI provider");
        }

        try {
            long started = System.nanoTime();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", config.model());
            body.put("stream", true);
            body.put("stream_options", Map.of("include_usage", true));
            body.put("messages", toMessages(prompt));
            OpenAiToolFormat.NormalizedTools normalizedTools = OpenAiToolFormat.normalize(
                    prompt.tools(), config.strictToolNames());
            if (!normalizedTools.tools().isEmpty()) {
                body.put("tools", normalizedTools.tools());
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl().replaceAll("/$", "") + "/chat/completions"))
                    .timeout(config.timeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(ProtocolJson.toJson(body)))
                    .build();

            HttpResponse<java.io.InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                String err = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException(config.providerLabel() + " HTTP " + response.statusCode() + ": " + err);
            }

            StreamAggregate agg = parseStream(response.body(), normalizedTools.apiToCanonical(), context.onTextDelta());
            long latencyMs = (System.nanoTime() - started) / 1_000_000L;
            ModelUsage usage = toUsage(agg, latencyMs);
            if (!agg.toolCalls.isEmpty()) {
                List<ToolCallIntent> calls = new ArrayList<>();
                for (var e : agg.toolCalls.entrySet()) {
                    calls.add(new ToolCallIntent(e.getKey(), e.getValue().name, e.getValue().arguments));
                }
                return Optional.of(new ModelTurn(null, calls, usage));
            }
            if (agg.text != null && !agg.text.isBlank()) {
                return Optional.of(new ModelTurn(agg.text, List.of(), usage));
            }
            return Optional.empty();
        } catch (Exception e) {
            throw new IllegalStateException(config.providerLabel() + " model call failed: " + e.getMessage(), e);
        }
    }

    private static List<Map<String, Object>> toMessages(PromptBundle prompt) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", prompt.instructions()));
        for (Map<String, Object> item : MessageHistorySanitizer.sanitize(prompt.input())) {
            String role = String.valueOf(item.getOrDefault("role", "user"));
            if ("developer".equals(role)) {
                messages.add(Map.of("role", "system", "content", String.valueOf(item.getOrDefault("content", ""))));
                continue;
            }
            if ("assistant".equals(role)) {
                Map<String, Object> msg = new LinkedHashMap<>();
                msg.put("role", "assistant");
                msg.put("content", String.valueOf(item.getOrDefault("content", "")));
                if (item.containsKey("tool_calls")) {
                    msg.put("tool_calls", item.get("tool_calls"));
                }
                messages.add(msg);
                continue;
            }
            if ("tool".equals(role)) {
                Map<String, Object> toolMsg = new LinkedHashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", item.get("tool_call_id"));
                toolMsg.put("content", String.valueOf(item.getOrDefault("content", "")));
                messages.add(toolMsg);
                continue;
            }
            messages.add(Map.of("role", role, "content", String.valueOf(item.getOrDefault("content", ""))));
        }
        return messages;
    }

    static StreamAggregate parseStream(java.io.InputStream input) throws Exception {
        return parseStream(input, Map.of(), null);
    }

    static StreamAggregate parseStream(java.io.InputStream input, Map<String, String> apiToCanonical) throws Exception {
        return parseStream(input, apiToCanonical, null);
    }

    static StreamAggregate parseStream(
            java.io.InputStream input, Map<String, String> apiToCanonical, Consumer<String> onTextDelta)
            throws Exception {
        StreamAggregate agg = new StreamAggregate();
        String raw = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        Map<Integer, ToolCallAccum> byIndex = new LinkedHashMap<>();

        for (String line : raw.split("\n")) {
            line = line.trim();
            if (!line.startsWith("data:")) {
                continue;
            }
            String data = line.substring(5).trim();
            if (data.isEmpty() || "[DONE]".equals(data)) {
                continue;
            }
            JsonNode node = ProtocolJson.mapper().readTree(data);
            if (node.has("usage") && !node.get("usage").isNull()) {
                agg.usage = node.get("usage");
            }
            JsonNode delta = node.path("choices").path(0).path("delta");
            if (delta.has("content")) {
                String piece = delta.get("content").asText("");
                if (!piece.isEmpty()) {
                    agg.text = (agg.text == null ? "" : agg.text) + piece;
                    if (onTextDelta != null) {
                        onTextDelta.accept(piece);
                    }
                }
            }
            if (delta.has("tool_calls")) {
                for (JsonNode tc : delta.get("tool_calls")) {
                    int index = tc.path("index").asInt(0);
                    ToolCallAccum accum = byIndex.computeIfAbsent(index, i -> new ToolCallAccum());
                    if (tc.has("id") && !tc.get("id").asText().isBlank()) {
                        accum.id = tc.get("id").asText();
                    }
                    JsonNode fn = tc.path("function");
                    if (fn.has("name")) {
                        accum.name = fn.get("name").asText();
                    }
                    if (fn.has("arguments")) {
                        accum.argBuffer = (accum.argBuffer == null ? "" : accum.argBuffer) + fn.get("arguments").asText();
                    }
                }
            }
        }

        for (var entry : byIndex.entrySet()) {
            ToolCallAccum accum = entry.getValue();
            if (accum.argBuffer != null && !accum.argBuffer.isBlank()) {
                JsonNode args = ProtocolJson.mapper().readTree(accum.argBuffer);
                @SuppressWarnings("unchecked")
                Map<String, Object> map = ProtocolJson.mapper().convertValue(args, Map.class);
                accum.arguments = map;
            }
            accum.name = OpenAiToolFormat.resolveCanonicalName(accum.name, apiToCanonical);
            String id = accum.id == null || accum.id.isBlank() ? "call_" + entry.getKey() : accum.id;
            agg.toolCalls.put(id, accum);
        }
        return agg;
    }

    private static ModelUsage toUsage(StreamAggregate agg, long latencyMs) {
        if (agg.usage != null && !agg.usage.isNull()) {
            long input = agg.usage.path("prompt_tokens").asLong(0);
            long output = agg.usage.path("completion_tokens").asLong(0);
            Long cached = null;
            JsonNode details = agg.usage.path("prompt_tokens_details");
            if (details.has("cached_tokens")) {
                cached = details.get("cached_tokens").asLong();
            }
            return new ModelUsage(input, output, cached, latencyMs);
        }
        return ModelUsage.estimate(0, 0, latencyMs);
    }

    static final class StreamAggregate {
        String text;
        JsonNode usage;
        Map<String, ToolCallAccum> toolCalls = new LinkedHashMap<>();
    }

    static final class ToolCallAccum {
        String id;
        String name;
        Map<String, Object> arguments = Map.of();
        String argBuffer;
    }
}
