package com.anvil.core.loop;

import com.anvil.core.compact.ContextBudget;
import com.anvil.core.compact.ContextCompactor;
import com.anvil.protocol.ToolResult;
import com.anvil.tools.EditSummary;
import com.anvil.tools.VerifyRunner;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Runs Maven verify after write tools and injects failures into agent history. */
final class VerifyPass {

    private static final Set<String> WRITE_TOOLS = Set.of("fs.write", "search_replace", "apply_patch", "edit.plan");

    private VerifyPass() {}

    static boolean isWriteTool(String name) {
        return name != null && WRITE_TOOLS.contains(name);
    }

    static EditSummary.Delta deltaFor(String toolName, Map<String, Object> args, String previousContent) {
        if (args == null || !isWriteTool(toolName)) {
            return null;
        }
        Object pathObj = args.get("path");
        if (pathObj == null) {
            return null;
        }
        String path = String.valueOf(pathObj);
        return switch (toolName) {
            case "search_replace" -> EditSummary.forReplace(
                    path,
                    stringArg(args, "old_string"),
                    stringArg(args, "new_string"),
                    Boolean.TRUE.equals(args.get("replace_all")) ? 2 : 1);
            case "apply_patch" -> EditSummary.forPatch(path, stringArg(args, "patch"));
            case "fs.write" -> EditSummary.forWrite(path, previousContent, stringArg(args, "content"));
            default -> null;
        };
    }

    static void maybeRun(
            RunContext ctx,
            Path workspaceRoot,
            VerifyConfig config,
            String toolName,
            Map<String, Object> args,
            ContextBudget budget,
            long shellTimeoutMs) {
        if (config == null || !config.autoAfterWrite() || !isWriteTool(toolName)) {
            return;
        }
        String path = args == null ? null : stringArg(args, "path");
        String command = EditSummary.inferVerifyCommand(workspaceRoot, path, config.commandTemplate());
        if (command == null || command.isBlank()) {
            return;
        }

        ctx.emit("verify.started", Map.of("command", command, "path", path == null ? "" : path, "trigger", toolName));

        String verifyId = "verify_" + System.nanoTime();
        long timeout = Math.max(config.timeoutMs(), shellTimeoutMs);
        ToolResult result = VerifyRunner.run(workspaceRoot, verifyId, command, timeout);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("command", command);
        payload.put("path", path == null ? "" : path);
        payload.put("status", result.status());
        if (result.content() != null && !result.content().isBlank()) {
            String preview = result.content().replace('\n', ' ').trim();
            payload.put("preview", preview.length() > 200 ? preview.substring(0, 197) + "..." : preview);
        }

        if ("ok".equals(result.status())) {
            ctx.clearVerifyFixRequired();
            ctx.emit("verify.completed", payload);
            return;
        }

        ctx.emit("verify.failed", payload);
        if (result.content() != null && !result.content().isBlank()) {
            ctx.anchors().recordFailure(result.content());
        }
        if (config.injectFailuresIntoHistory()) {
            injectVerifyHistory(ctx, verifyId, result, budget);
        }
        if (config.forceFixOnFailure()) {
            ctx.setVerifyFixRequired(true);
            injectDeveloperFix(ctx, result.content());
        }
    }

    private static void injectVerifyHistory(RunContext ctx, String verifyId, ToolResult result, ContextBudget budget) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("role", "tool");
        item.put("tool_call_id", verifyId);
        item.put("name", "verify.auto");
        item.put("status", "error");
        item.put(
                "content",
                ContextCompactor.truncateContent(result.content(), budget.maxToolContentChars())
                        + "\n\nFix the errors above before continuing.");
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
                        Verify failed after your edit. You MUST fix all test/compile errors before completing the run.
                        Apply fixes with search_replace or apply_patch — do not finish with a text-only reply until verify passes.
                        """
                                .trim()
                                + (snippet.isBlank() ? "" : "\nPreview: " + snippet)));
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? "" : String.valueOf(v);
    }
}
