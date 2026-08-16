package com.anvil.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Deterministic model from JSONL script (tests / fixtures). */
public final class ScriptedModel implements ModelProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<JsonNode> script;
    private int index;

    public ScriptedModel(Path jsonlPath) throws IOException {
        this.script = load(jsonlPath);
    }

    public ScriptedModel(List<JsonNode> script) {
        this.script = List.copyOf(script);
    }

    @Override
    public Optional<ModelTurn> nextTurn(ModelTurnContext context) {
        if (index >= script.size()) {
            return Optional.empty();
        }
        JsonNode line = script.get(index++);
        String type = line.path("type").asText();
        if ("message".equals(type)) {
            String text = line.path("text").asText();
            emitScriptedDeltas(context, text);
            return Optional.of(new ModelTurn(text, List.of()));
        }
        if ("tool_call".equals(type)) {
            String id = line.path("id").asText("call_" + index);
            String name = line.path("name").asText();
            Map<String, Object> args = jsonObjectToMap(line.path("arguments"));
            return Optional.of(new ModelTurn(null, List.of(new ToolCallIntent(id, name, args))));
        }
        throw new IllegalStateException("unknown scripted line type: " + type);
    }

    private static void emitScriptedDeltas(ModelTurnContext context, String text) {
        Consumer<String> onDelta = context.onTextDelta();
        if (onDelta == null || text == null || text.isEmpty()) {
            return;
        }
        int chunk = Math.max(1, Math.min(8, text.length() / 4));
        for (int i = 0; i < text.length(); i += chunk) {
            onDelta.accept(text.substring(i, Math.min(text.length(), i + chunk)));
        }
    }

    private static List<JsonNode> load(Path path) throws IOException {
        List<JsonNode> lines = new ArrayList<>();
        for (String raw : Files.readAllLines(path)) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            lines.add(MAPPER.readTree(trimmed));
        }
        return lines;
    }

    private static Map<String, Object> jsonObjectToMap(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return map;
        }
        node.fields().forEachRemaining(e -> map.put(e.getKey(), jsonValue(e.getValue())));
        return map;
    }

    private static Object jsonValue(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isObject()) {
            return jsonObjectToMap(node);
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(n -> list.add(jsonValue(n)));
            return list;
        }
        return node.asText();
    }
}
