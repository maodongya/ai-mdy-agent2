package com.anvil.tools;

import com.anvil.protocol.ErrorCodes;
import com.anvil.protocol.ErrorInfo;
import com.anvil.protocol.SideEffect;
import com.anvil.protocol.ToolResult;
import com.anvil.sandbox.PathEscapeException;
import com.anvil.sandbox.PathGuard;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class FsTools {

    private static final int MAX_READ_CHARS = 200_000;
    private static final int MAX_GLOB = 500;

    private final Path workspaceRoot;

    public FsTools(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
    }

    public Path workspaceRoot() {
        return workspaceRoot;
    }

    public ToolResult read(String toolCallId, String path) {
        return read(toolCallId, path, null, null);
    }

    public ToolResult read(String toolCallId, String path, Integer offset, Integer limit) {
        try {
            Path abs = PathGuard.assertInsideWorkspace(workspaceRoot, path);
            if (!Files.isRegularFile(abs)) {
                return error(toolCallId, "fs.read", ErrorCodes.TOOL_FAILED, "not a file: " + path);
            }
            String text = Files.readString(abs);
            text = sliceLines(text, offset, limit);
            boolean truncated = false;
            if (text.length() > MAX_READ_CHARS) {
                text = text.substring(0, MAX_READ_CHARS) + "\n...[truncated]";
                truncated = true;
            }
            return new ToolResult(toolCallId, "fs.read", "ok", text, truncated, null, null);
        } catch (PathEscapeException e) {
            return denied(toolCallId, "fs.read", e.getMessage());
        } catch (IOException e) {
            return error(toolCallId, "fs.read", ErrorCodes.TOOL_FAILED, e.getMessage());
        } catch (IllegalArgumentException e) {
            return error(toolCallId, "fs.read", ErrorCodes.TOOL_ARG_INVALID, e.getMessage());
        }
    }

    static String sliceLines(String text, Integer offset, Integer limit) {
        if (offset == null && limit == null) {
            return text;
        }
        if (text == null || text.isEmpty()) {
            return "";
        }
        String[] lines = text.split("\n", -1);
        int start = offset == null ? 0 : Math.max(0, offset - 1);
        if (start >= lines.length) {
            throw new IllegalArgumentException("offset " + offset + " exceeds file length (" + lines.length + " lines)");
        }
        int end = limit == null ? lines.length : Math.min(lines.length, start + Math.max(0, limit));
        if (end <= start) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    public ToolResult write(String toolCallId, String path, String content) {
        try {
            Path abs = PathGuard.assertInsideWorkspace(workspaceRoot, path);
            String body = content == null ? "" : content;
            int lineCount = countLines(body);
            if (lineCount > EditTools.maxWriteLinesHint()) {
                return error(
                        toolCallId,
                        "fs.write",
                        ErrorCodes.TOOL_ARG_INVALID,
                        "refusing fs.write: " + lineCount + " lines exceeds limit of "
                                + EditTools.maxWriteLinesHint()
                                + ". Use search_replace or apply_patch for large edits.");
            }
            Files.createDirectories(abs.getParent() != null ? abs.getParent() : workspaceRoot);
            Files.writeString(abs, body);
            return ToolResult.ok(toolCallId, "fs.write", "wrote " + path + " (" + body.length() + " chars)");
        } catch (PathEscapeException e) {
            return denied(toolCallId, "fs.write", e.getMessage());
        } catch (IOException e) {
            return error(toolCallId, "fs.write", ErrorCodes.TOOL_FAILED, e.getMessage());
        }
    }

    private static int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.split("\n", -1).length;
    }

    public ToolResult glob(String toolCallId, String pattern) {
        String glob = pattern == null || pattern.isBlank() ? "**/*" : pattern;
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
        List<String> matches = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(workspaceRoot)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                Path rel = workspaceRoot.relativize(p);
                String posix = rel.toString().replace('\\', '/');
                if (matcher.matches(rel) || matcher.matches(Path.of(posix))) {
                    matches.add(posix);
                }
            });
        } catch (IOException e) {
            return error(toolCallId, "fs.glob", ErrorCodes.TOOL_FAILED, e.getMessage());
        }
        matches.sort(Comparator.naturalOrder());
        boolean truncated = matches.size() > MAX_GLOB;
        List<String> slice = matches.subList(0, Math.min(matches.size(), MAX_GLOB));
        return new ToolResult(toolCallId, "fs.glob", "ok", String.join("\n", slice), truncated, null, null);
    }

    private static ToolResult denied(String toolCallId, String name, String message) {
        return ToolResult.denied(toolCallId, name, ErrorInfo.of(ErrorCodes.POLICY_DENIED, message, false));
    }

    private static ToolResult error(String toolCallId, String name, String code, String message) {
        return new ToolResult(toolCallId, name, "error", "", false, null, ErrorInfo.of(code, message, false));
    }

    public static SideEffect sideEffectFor(String toolName) {
        return switch (toolName) {
            case "fs.read", "fs.glob" -> SideEffect.READ;
            case "fs.write", "fs.apply_patch", "plan.update" -> SideEffect.WRITE_WORKSPACE;
            case "shell.exec" -> SideEffect.EXEC;
            default -> SideEffect.EXTERNAL_SIDE_EFFECT;
        };
    }

    public static ToolResult execute(FsTools fs, String name, String toolCallId, Map<String, Object> args) {
        Map<String, Object> normalized = ToolArgNormalizer.normalize(name, args);
        try {
            return switch (name) {
                case "fs.read" -> fs.read(
                        toolCallId,
                        requiredArg(normalized, "path"),
                        intArg(normalized, "offset"),
                        intArg(normalized, "limit"));
                case "fs.write" -> fs.write(
                        toolCallId,
                        requiredArg(normalized, "path"),
                        stringArg(normalized, "content", ""));
                case "fs.glob" -> fs.glob(toolCallId, stringArg(normalized, "pattern", "**/*"));
                default -> error(toolCallId, name, ErrorCodes.TOOL_ARG_INVALID, "unknown tool: " + name);
            };
        } catch (IllegalArgumentException e) {
            return error(toolCallId, name, ErrorCodes.TOOL_ARG_INVALID, e.getMessage());
        }
    }

    private static String requiredArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            throw new IllegalArgumentException(
                    "missing arg: " + key + " (received keys: " + args.keySet() + ")");
        }
        return String.valueOf(v);
    }

    private static Integer intArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid integer for " + key + ": " + v);
        }
    }

    private static String stringArg(Map<String, Object> args, String key, String defaultValue) {
        Object v = args.get(key);
        return v == null ? defaultValue : String.valueOf(v);
    }
}
