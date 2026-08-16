package com.anvil.tools;

import com.anvil.protocol.ErrorCodes;
import com.anvil.protocol.ErrorInfo;
import com.anvil.protocol.ToolResult;
import com.anvil.sandbox.PathGuard;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Batch edit plan: validate and atomically apply multiple operations (Phase 7.5). */
public final class EditPlanTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EditPlanTool() {}

    public record Operation(String path, String old_string, String new_string, String patch) {}

    public static ToolResult execute(FsTools fs, String toolCallId, String operationsJson) {
        if (operationsJson == null || operationsJson.isBlank()) {
            return argError(toolCallId, "operations JSON array is required");
        }
        List<Operation> ops;
        try {
            ops = MAPPER.readValue(operationsJson, new TypeReference<>() {});
        } catch (Exception e) {
            return argError(toolCallId, "invalid operations JSON: " + e.getMessage());
        }
        if (ops.isEmpty()) {
            return argError(toolCallId, "operations must not be empty");
        }

        Map<Path, String> backups = new LinkedHashMap<>();
        List<String> touched = new ArrayList<>();
        try {
            for (Operation op : ops) {
                if (op.path() == null || op.path().isBlank()) {
                    rollback(backups);
                    return error(toolCallId, "each operation requires path");
                }
                String path = op.path().trim();
                Path abs = PathGuard.assertInsideWorkspace(fs.workspaceRoot(), path);
                backups.putIfAbsent(abs, Files.isRegularFile(abs) ? Files.readString(abs) : "");
                touched.add(path);

                ToolResult step;
                if (op.patch() != null && !op.patch().isBlank()) {
                    step = EditTools.applyPatch(fs, toolCallId + "_p", path, op.patch());
                } else {
                    if (op.old_string() == null || op.old_string().isEmpty()) {
                        rollback(backups);
                        return error(toolCallId, "operation for " + path + " needs old_string or patch");
                    }
                    step = EditTools.searchReplace(
                            fs,
                            toolCallId + "_sr",
                            path,
                            op.old_string(),
                            op.new_string() == null ? "" : op.new_string(),
                            false);
                }
                if (!"ok".equals(step.status())) {
                    rollback(backups);
                    return error(toolCallId, stepError(step, path));
                }
            }

            StringBuilder sb = new StringBuilder("applied edit plan to " + touched.size() + " file(s):\n");
            for (String p : touched) {
                sb.append("- ").append(p).append('\n');
            }
            return ToolResult.ok(toolCallId, "edit.plan", sb.toString().trim());
        } catch (Exception e) {
            rollback(backups);
            return error(toolCallId, e.getMessage());
        }
    }

    public static String summarize(String operationsJson) {
        try {
            List<Operation> ops = MAPPER.readValue(operationsJson, new TypeReference<>() {});
            StringBuilder sb = new StringBuilder();
            sb.append("edit plan: ").append(ops.size()).append(" operation(s)\n");
            for (Operation op : ops) {
                sb.append("- ").append(op.path());
                if (op.patch() != null && !op.patch().isBlank()) {
                    sb.append(" (patch)");
                } else {
                    sb.append(" (search_replace)");
                }
                sb.append('\n');
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "edit plan (unparsed)";
        }
    }

    private static void rollback(Map<Path, String> backups) {
        for (var e : backups.entrySet()) {
            try {
                Path parent = e.getKey().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(e.getKey(), e.getValue());
            } catch (Exception ignored) {
                // best effort
            }
        }
    }

    private static String stepError(ToolResult r, String path) {
        String msg = r.error() != null ? r.error().message() : r.content();
        return "failed for " + path + ": " + msg;
    }

    private static ToolResult argError(String toolCallId, String message) {
        return new ToolResult(
                toolCallId,
                "edit.plan",
                "error",
                "",
                false,
                null,
                ErrorInfo.of(ErrorCodes.TOOL_ARG_INVALID, message, false));
    }

    private static ToolResult error(String toolCallId, String message) {
        return new ToolResult(
                toolCallId,
                "edit.plan",
                "error",
                "",
                false,
                null,
                ErrorInfo.of(ErrorCodes.TOOL_FAILED, message, false));
    }
}
