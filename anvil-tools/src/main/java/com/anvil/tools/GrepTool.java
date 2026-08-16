package com.anvil.tools;

import com.anvil.protocol.ErrorCodes;
import com.anvil.protocol.ErrorInfo;
import com.anvil.protocol.ToolResult;
import com.anvil.sandbox.PathEscapeException;
import com.anvil.sandbox.PathGuard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/** Workspace grep (Java implementation, no shell required). */
public final class GrepTool {

    private static final int DEFAULT_MAX_MATCHES = 200;
    private static final int MAX_LINE_PREVIEW = 300;

    private static final Set<String> IGNORED_DIR_NAMES = Set.of(
            ".git", ".idea", ".gradle", ".mvn", ".vscode", "target", "build", "out", "dist", "node_modules", "__pycache__");

    private GrepTool() {}

    public static ToolResult grep(
            Path workspaceRoot,
            String toolCallId,
            String pattern,
            String pathGlob,
            boolean caseInsensitive,
            Integer maxMatches) {

        if (pattern == null || pattern.isBlank()) {
            return error(toolCallId, ErrorCodes.TOOL_ARG_INVALID, "pattern is required");
        }

        int limit = maxMatches == null || maxMatches <= 0 ? DEFAULT_MAX_MATCHES : Math.min(maxMatches, 500);
        Pattern regex;
        try {
            int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
            regex = Pattern.compile(pattern, flags);
        } catch (PatternSyntaxException e) {
            return error(toolCallId, ErrorCodes.TOOL_ARG_INVALID, "invalid regex: " + e.getMessage());
        }

        Path root = workspaceRoot.toAbsolutePath().normalize();
        List<String> lines = new ArrayList<>();
        int matches = 0;
        boolean truncated = false;

        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).filter(p -> !isUnderIgnored(root, p)).toList()) {
                if (matches >= limit) {
                    truncated = true;
                    break;
                }
                String rel = root.relativize(file).toString().replace('\\', '/');
                try {
                    PathGuard.assertInsideWorkspace(root, rel);
                } catch (PathEscapeException e) {
                    continue;
                }
                if (pathGlob != null && !pathGlob.isBlank() && !GlobTool.matches(rel, pathGlob)) {
                    continue;
                }
                if (!isTextCandidate(file)) {
                    continue;
                }
                List<String> fileLines;
                try {
                    fileLines = Files.readAllLines(file, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    continue;
                }
                for (int i = 0; i < fileLines.size(); i++) {
                    if (matches >= limit) {
                        truncated = true;
                        break;
                    }
                    String line = fileLines.get(i);
                    if (regex.matcher(line).find()) {
                        matches++;
                        String preview = line.length() <= MAX_LINE_PREVIEW
                                ? line
                                : line.substring(0, MAX_LINE_PREVIEW) + "...";
                        lines.add(rel + ":" + (i + 1) + ":" + preview);
                    }
                }
            }
        } catch (IOException e) {
            return error(toolCallId, ErrorCodes.TOOL_FAILED, e.getMessage());
        }

        if (lines.isEmpty()) {
            return ToolResult.ok(toolCallId, "grep", "no matches");
        }
        String body = String.join("\n", lines);
        if (truncated) {
            body += "\n...[truncated at " + limit + " matches]";
        }
        return new ToolResult(toolCallId, "grep", "ok", body, truncated, null, null);
    }

    private static boolean isUnderIgnored(Path root, Path path) {
        for (Path part : root.relativize(path)) {
            if (IGNORED_DIR_NAMES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTextCandidate(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".class")
                || name.endsWith(".jar")
                || name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".gif")
                || name.endsWith(".zip")
                || name.endsWith(".woff")
                || name.endsWith(".woff2")) {
            return false;
        }
        return true;
    }

    private static ToolResult error(String toolCallId, String code, String message) {
        return new ToolResult(toolCallId, "grep", "error", "", false, null, ErrorInfo.of(code, message, false));
    }
}
