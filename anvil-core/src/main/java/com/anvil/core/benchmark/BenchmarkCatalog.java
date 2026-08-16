package com.anvil.core.benchmark;

import com.anvil.protocol.ProtocolJson;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads benchmark specs from {@code fixtures/benchmarks/}. */
public final class BenchmarkCatalog {

    private BenchmarkCatalog() {}

    public static Path benchmarksDir(Path repoRoot) {
        return repoRoot.resolve("fixtures/benchmarks");
    }

    public static List<BenchmarkSpec> loadAll(Path repoRoot) throws IOException {
        Path manifest = benchmarksDir(repoRoot).resolve("manifest.json");
        JsonNode root = ProtocolJson.mapper().readTree(Files.readString(manifest));
        List<BenchmarkSpec> specs = new ArrayList<>();
        for (JsonNode idNode : root.get("benchmarks")) {
            specs.add(load(repoRoot, idNode.asText()));
        }
        return List.copyOf(specs);
    }

    public static BenchmarkSpec load(Path repoRoot, String id) throws IOException {
        Path file = benchmarksDir(repoRoot).resolve(id + ".benchmark.json");
        JsonNode root = ProtocolJson.mapper().readTree(Files.readString(file));
        JsonNode expectNode = root.get("expect");

        BenchmarkSpec.BenchmarkExpect expect = new BenchmarkSpec.BenchmarkExpect(
                text(expectNode, "status"),
                stringList(expectNode, "event_types"),
                stringList(expectNode, "event_types_contains"),
                stringList(expectNode, "forbid_event_types"),
                intOrNull(expectNode, "max_tool_calls"),
                intOrNull(expectNode, "max_step_events"),
                stringMap(expectNode, "file_contains"),
                stringMap(expectNode, "file_equals"),
                stringList(expectNode, "file_exists"));

        return new BenchmarkSpec(
                root.get("id").asText(),
                text(root, "name"),
                text(root, "description"),
                text(root, "workspace"),
                text(root, "workspace_from"),
                root.get("model").asText(),
                text(root, "mode"),
                root.get("user_message").asText(),
                root.path("max_steps").asInt(15),
                expect);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private static Integer intOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asInt();
    }

    private static List<String> stringList(JsonNode node, String field) {
        if (node == null || !node.has(field) || !node.get(field).isArray()) {
            return null;
        }
        List<String> list = new ArrayList<>();
        node.get(field).forEach(n -> list.add(n.asText()));
        return list.isEmpty() ? null : List.copyOf(list);
    }

    private static Map<String, String> stringMap(JsonNode node, String field) {
        if (node == null || !node.has(field) || !node.get(field).isObject()) {
            return null;
        }
        Map<String, String> map = new LinkedHashMap<>();
        node.get(field).fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().asText()));
        return map.isEmpty() ? null : Map.copyOf(map);
    }
}
