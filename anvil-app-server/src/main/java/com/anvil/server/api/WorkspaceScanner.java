package com.anvil.server.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 遍历工作区根目录，返回一层的路径节点列表（目录和文件），用于构建 UI 文件树。
 *
 * <p>扫描器执行有界的目录遍历，最多深入 {@link #MAX_DEPTH} 层。它会先对根路径
 * 进行规范化（绝对路径 + 去掉冗余路径段），跳过被忽略的构建/工具目录，并对每个节点
 * 输出其相对路径（使用正斜杠）以及类型（"dir" 表示目录，"file" 表示文件）。
 *
 * <p>该类刻意设计为无状态、仅包含静态成员；它由
 * {@link com.anvil.server.api.WorkspaceController} 使用，用于填充 Workbench UI 的
 * 工作区浏览器。
 */
public final class WorkspaceScanner {

    /**
     * 遍历工作区时向下递归的最大目录深度。
     *
     * <p>该值足以覆盖常见的 Maven/Gradle {@code src/main/java/...} 源码目录结构，
     * 同时避免遍历过深的目录树（例如生成代码或 vendor 目录）造成无限递归。
     */
    public static final int MAX_DEPTH = 20;

    /**
     * 绝不应出现在工作区树中的目录名称。
     *
     * <p>这些涵盖常见的 VCS 元数据、IDE 工程目录以及构建输出目录。它们与 UI 文件
     * 浏览器无关，若不屏蔽会让文件树塞满噪音，因此统一过滤掉。
     */
    private static final Set<String> IGNORED_DIR_NAMES = Set.of(
            ".git",
            ".idea",
            ".gradle",
            ".mvn",
            ".vscode",
            "target",
            "build",
            "out",
            "dist",
            "node_modules",
            "__pycache__");

    /** 工具类 —— 不允许实例化。 */
    private WorkspaceScanner() {}

    /**
     * 扫描给定的工作区根目录，返回一层的路径节点列表。
     *
     * @param root 工作区根目录。若不存在或不是目录，则返回空列表。
     * @return 一个 map 列表，每个 map 含 {@code "path"} 键（相对路径，正斜杠，
     *         不含根目录本身）和 {@code "type"} 键（目录为 "dir"，文件为 "file"）
     * @throws IOException 遍历目录树时发生 I/O 错误
     */
    static List<Map<String, Object>> scan(Path root) throws IOException {
        List<Map<String, Object>> nodes = new ArrayList<>();
        Path normalized = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            return nodes;
        }
        try (var walk = Files.walk(normalized, MAX_DEPTH)) {
            walk.filter(p -> !p.equals(normalized))
                    .filter(p -> !isUnderIgnoredDir(normalized, p))
                    .forEach(p -> {
                        String rel = normalized.relativize(p).toString().replace('\\', '/');
                        nodes.add(Map.of(
                                "path", rel,
                                "type", Files.isDirectory(p) ? "dir" : "file"));
                    });
        }
        return nodes;
    }

    /**
     * 判断给定路径是否位于某个被忽略的目录名称之下（见 {@link #IGNORED_DIR_NAMES}）。
     * 该检查作用于根目录相对路径的每一个路径段，因此在任意深度都会被过滤掉。
     *
     * @param root 规范化后的工作区根目录
     * @param path 待检查的路径（相对于 {@code root}，既可以是相对路径也可以是绝对路径）
     * @return 若任一路径段属于被忽略的目录，则返回 {@code true}
     */
    private static boolean isUnderIgnoredDir(Path root, Path path) {
        for (Path part : root.relativize(path)) {
            if (IGNORED_DIR_NAMES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }
}
