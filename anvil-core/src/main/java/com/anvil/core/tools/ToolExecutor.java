package com.anvil.core.tools;

import com.anvil.core.mcp.McpBridge;
import com.anvil.protocol.ErrorCodes;
import com.anvil.protocol.ErrorInfo;
import com.anvil.protocol.ToolResult;
import com.anvil.tools.CodebaseSearchTool;
import com.anvil.tools.DiagnosticsTool;
import com.anvil.tools.EditTools;
import com.anvil.tools.FsTools;
import com.anvil.tools.GitTools;
import com.anvil.tools.GrepTool;
import com.anvil.tools.PlanTool;
import com.anvil.tools.ShellTool;
import com.anvil.tools.SymbolsSearchTool;
import com.anvil.tools.ToolArgNormalizer;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class ToolExecutor {

    private final FsTools fsTools;
    private final long shellTimeoutMs;
    private final McpBridge mcpBridge;

    public ToolExecutor(Path workspaceRoot, long shellTimeoutMs) {
        this(workspaceRoot, shellTimeoutMs, null);
    }

    public ToolExecutor(Path workspaceRoot, long shellTimeoutMs, McpBridge mcpBridge) {
        this.fsTools = new FsTools(workspaceRoot);
        this.shellTimeoutMs = shellTimeoutMs;
        this.mcpBridge = mcpBridge;
    }

    public FsTools fsTools() {
        return fsTools;
    }

    public ToolResult execute(String toolCallId, String name, Map<String, Object> arguments) {
        Map<String, Object> args = ToolArgNormalizer.normalize(name, arguments);
        if (mcpBridge != null && mcpBridge.isMcpTool(name)) {
            return mcpBridge.execute(toolCallId, name, args);
        }
        return switch (name) {
            case "grep" -> GrepTool.grep(
                    fsTools.workspaceRoot(),
                    toolCallId,
                    stringArg(args, "pattern", ""),
                    stringArg(args, "path_glob", null),
                    boolArg(args, "case_insensitive", false),
                    intArg(args, "max_matches"));
            case "codebase.search" -> CodebaseSearchTool.search(
                    fsTools.workspaceRoot(), toolCallId, stringArg(args, "query", ""), intArg(args, "top_k"));
            case "symbols.search" -> SymbolsSearchTool.search(
                    fsTools.workspaceRoot(), toolCallId, stringArg(args, "query", ""), intArg(args, "top_k"));
            case "search_replace" -> EditTools.searchReplace(
                    fsTools,
                    toolCallId,
                    stringArg(args, "path", ""),
                    stringArg(args, "old_string", ""),
                    stringArg(args, "new_string", ""),
                    boolArg(args, "replace_all", false));
            case "apply_patch" -> EditTools.applyPatch(
                    fsTools, toolCallId, stringArg(args, "path", ""), stringArg(args, "patch", ""));
            case "git.status" -> GitTools.status(fsTools.workspaceRoot(), toolCallId);
            case "git.diff" -> GitTools.diffStat(fsTools.workspaceRoot(), toolCallId);
            case "diagnostics.collect" -> DiagnosticsTool.collect(
                    fsTools.workspaceRoot(),
                    toolCallId,
                    stringArg(args, "scope", "compile"),
                    shellTimeoutMs);
            case "plan.update" -> PlanTool.update(toolCallId, fsTools, stringArg(args, "content", ""));
            case "shell.exec" -> ShellTool.exec(toolCallId, args, fsTools.workspaceRoot(), shellTimeoutMs);
            default -> FsTools.execute(fsTools, name, toolCallId, args);
        };
    }

    public static Map<String, Object> previewFor(String name, Map<String, Object> arguments) {
        if (name != null && name.startsWith("mcp.")) {
            return Map.of("summary", name, "mcp", true);
        }
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        return switch (name) {
            case "fs.write", "search_replace", "apply_patch" -> Map.of(
                    "summary", name + " " + args.get("path"),
                    "paths", List.of(String.valueOf(args.get("path"))));
            case "plan.update" -> Map.of(
                    "summary", "update plan",
                    "paths", List.of(PlanTool.PLAN_PATH));
            case "shell.exec" -> Map.of(
                    "summary", "exec: " + args.get("command"),
                    "command", String.valueOf(args.getOrDefault("command", "")));
            case "grep" -> Map.of("summary", "grep: " + args.get("pattern"));
            case "codebase.search" -> Map.of("summary", "search: " + args.get("query"));
            case "symbols.search" -> Map.of("summary", "symbols: " + args.get("query"));
            case "diagnostics.collect" -> Map.of("summary", "diagnostics: " + args.getOrDefault("scope", "compile"));
            default -> Map.of("summary", name);
        };
    }

    private static String stringArg(Map<String, Object> args, String key, String defaultValue) {
        Object v = args.get(key);
        return v == null ? defaultValue : String.valueOf(v);
    }

    private static boolean boolArg(Map<String, Object> args, String key, boolean defaultValue) {
        Object v = args.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(v));
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
            return null;
        }
    }

    public static ToolResult invalidArgs(String toolCallId, String name, String message) {
        return new ToolResult(
                toolCallId,
                name,
                "error",
                "",
                false,
                null,
                ErrorInfo.of(ErrorCodes.TOOL_ARG_INVALID, message, false));
    }
}
