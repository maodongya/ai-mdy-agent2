package com.anvil.core.loop;

import com.anvil.core.compact.ContextBudget;
import com.anvil.core.compact.ContextCompactor;
import com.anvil.protocol.ToolResult;
import com.anvil.tools.DiagnosticsTool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Lightweight compile diagnostics after writes (Phase 8.2). */
final class DiagnosticsPass {

    private static final Set<String> WRITE_TOOLS = Set.of("fs.write", "search_replace", "apply_patch", "edit.plan");
    private static final long COMPILE_TIMEOUT_MS = 90_000L;

    private DiagnosticsPass() {}

    static void maybeRun(
            RunContext ctx,
            Path workspaceRoot,
            VerifyConfig config,
            String toolName,
            Map<String, Object> args,
            ContextBudget budget,
            long shellTimeoutMs) {
        if (config == null || !config.autoCompileAfterWrite() || !isWriteTool(toolName)) {
            return;
        }
        if (!Files.exists(workspaceRoot.resolve("pom.xml"))) {
            return;
        }
        String path = args == null ? null : stringArg(args, "path");
        if (path != null && !isJavaSource(path)) {
            return;
        }

        String diagId = "diag_" + System.nanoTime();
        long timeout = Math.min(COMPILE_TIMEOUT_MS, Math.max(shellTimeoutMs, 30_000L));
        ctx.emit(
                "diagnostics.auto.started",
                Map.of("scope", "compile", "path", path == null ? "" : path, "trigger", toolName));

        ToolResult result = DiagnosticsTool.collect(workspaceRoot, diagId, "compile", timeout);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", "compile");
        payload.put("path", path == null ? "" : path);
        payload.put("status", result.status());
        if (result.content() != null && !result.content().isBlank()) {
            String preview = result.content().replace('\n', ' ').trim();
            payload.put("preview", preview.length() > 240 ? preview.substring(0, 237) + "..." : preview);
        }

        if ("ok".equals(result.status())) {
            ctx.emit("diagnostics.auto.completed", payload);
            return;
        }

        ctx.emit("diagnostics.auto.failed", payload);
        if (result.content() != null && !result.content().isBlank()) {
            ctx.anchors().recordFailure(result.content());
        }
        if (config.injectFailuresIntoHistory()) {
            injectDiagnosticsHistory(ctx, diagId, result, budget);
        }
        if (config.forceFixOnFailure()) {
            ctx.setVerifyFixRequired(true);
            injectDeveloperFix(ctx, result.content());
        }
    }

    private static boolean isWriteTool(String name) {
        return name != null && WRITE_TOOLS.contains(name);
    }

    private static boolean isJavaSource(String path) {
        return path != null && path.endsWith(".java");
    }

    private static void injectDiagnosticsHistory(
            RunContext ctx, String diagId, ToolResult result, ContextBudget budget) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("role", "tool");
        item.put("tool_call_id", diagId);
        item.put("name", "diagnostics.auto");
        item.put("status", "error");
        item.put(
                "content",
                ContextCompactor.truncateContent(result.content(), budget.maxToolContentChars())
                        + "\n\nFix compile errors before continuing.");
        ctx.appendHistory(item);
    }

    private static void injectDeveloperFix(RunContext ctx, String content) {
        String snippet = content == null ? "" : content.replace('\n', ' ').trim();
        if (snippet.length() > 200) {
            snippet = snippet.substring(0, 197) + "...";
        }
        ctx.appendHistory(
                Map.of(
                        "role",
                        "developer",
                        "content",
                        """
                        Compile/diagnostics failed after your edit. You MUST fix all errors before completing the run.
                        Use search_replace or apply_patch — do not respond with a summary until verify passes.
                        """
                                .trim()
                                + (snippet.isBlank() ? "" : "\nPreview: " + snippet)));
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? "" : String.valueOf(v);
    }
}
