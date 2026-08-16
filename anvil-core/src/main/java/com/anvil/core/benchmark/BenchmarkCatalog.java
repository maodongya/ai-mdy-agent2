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

/**
 * 基准测试目录加载器：从 {@code fixtures/benchmarks/} 目录读取基准测试规格。
 *
 * <p>每个基准测试对应一个 {@code *.benchmark.json} 文件（含任务描述、
 * 模型、模式与预期约束），manifest.json 声明了参与回归的基准列表。
 * 同时支持读取 {@code benchmark-live.json}，其条目需连接外部模型才能运行。</p>
 */
public final class BenchmarkCatalog {

    private BenchmarkCatalog() {}

    /**
     * 返回基准测试目录所在路径。
     *
     * @param repoRoot 仓库根目录
     * @return {@code {repoRoot}/fixtures/benchmarks} 路径
     */
    public static Path benchmarksDir(Path repoRoot) {
        return repoRoot.resolve("fixtures/benchmarks");
    }

    /**
     * 加载 manifest.json 中声明的全部基准测试规格。
     *
     * @param repoRoot 仓库根目录
     * @return 基准测试规格不可变列表
     */
    public static List<BenchmarkSpec> loadAll(Path repoRoot) throws IOException {
        Path manifest = benchmarksDir(repoRoot).resolve("manifest.json");
        JsonNode root = ProtocolJson.mapper().readTree(Files.readString(manifest));
        List<BenchmarkSpec> specs = new ArrayList<>();
        for (JsonNode idNode : root.get("benchmarks")) {
            specs.add(load(repoRoot, idNode.asText()));
        }
        return List.copyOf(specs);
    }

    /**
     * 加载单个基准测试规格。
     *
     * <p>解析规格文件的元信息（id / 名称 / 描述 / 工作区 / 模型 / 模式 / 用户消息，
     * 最大步数，是否为 live 基准），并将其中的期待约束（expect）解析为
     * {@link BenchmarkSpec.BenchmarkExpect}。</p>
     *
     * @param repoRoot 仓库根目录
     * @param id       基准测试 id（对应 {@code id.benchmark.json} 文件名）
     * @return 解析完成的基准测试规格
     */
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
                expect,
                root.path("live").asBoolean(false));
    }

    /**
     * 加载 live 基准测试清单：需要连接外部模型、不作为默认回归集的基准。
     *
     * @param repoRoot 仓库根目录
     * @return live 基准测试规格不可变列表；无清单文件时返回空列表
     */
    public static List<BenchmarkSpec> loadLive(Path repoRoot) throws IOException {
        Path manifest = benchmarksDir(repoRoot).resolve("benchmark-live.json");
        if (!Files.isRegularFile(manifest)) {
            return List.of();
        }
        JsonNode root = ProtocolJson.mapper().readTree(Files.readString(manifest));
        List<BenchmarkSpec> specs = new ArrayList<>();
        for (JsonNode idNode : root.get("benchmarks")) {
            specs.add(load(repoRoot, idNode.asText()));
        }
        return List.copyOf(specs);
    }

    /**
     * 读取字符串字段（缺失或为 null 时返回 null）。
     */
    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    /**
     * 读取整数字段（缺失或为 null 时返回 null）。
     */
    private static Integer intOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asInt();
    }

    /**
     * 读取字符串列表字段（缺失、非数组或空列表时返回 null）。
     */
    private static List<String> stringList(JsonNode node, String field) {
        if (node == null || !node.has(field) || !node.get(field).isArray()) {
            return null;
        }
        List<String> list = new ArrayList<>();
        node.get(field).forEach(n -> list.add(n.asText()));
        return list.isEmpty() ? null : List.copyOf(list);
    }

    /**
     * 读取字符串映射字段（缺失、非对象或为空时返回 null）。
     */
    private static Map<String, String> stringMap(JsonNode node, String field) {
        if (node == null || !node.has(field) || !node.get(field).isObject()) {
            return null;
        }
        Map<String, String> map = new LinkedHashMap<>();
        node.get(field).fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().asText()));
        return map.isEmpty() ? null : Map.copyOf(map);
    }
}
