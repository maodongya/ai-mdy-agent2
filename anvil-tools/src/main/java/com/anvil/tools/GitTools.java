package com.anvil.tools;

import com.anvil.protocol.ErrorCodes;
import com.anvil.protocol.ErrorInfo;
import com.anvil.protocol.ToolResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Git read-only helpers with friendly errors when not a repo. */
public final class GitTools {

    private static final int MAX_OUTPUT = 32_000;

    private GitTools() {}

    public static ToolResult status(Path workspaceRoot, String toolCallId) {
        return runGit(workspaceRoot, toolCallId, "git.status", "status", "--short", "--branch");
    }

    public static ToolResult diffStat(Path workspaceRoot, String toolCallId) {
        return runGit(workspaceRoot, toolCallId, "git.diff", "diff", "--stat");
    }

    private static ToolResult runGit(Path workspaceRoot, String toolCallId, String toolName, String... args) {
        if (!Files.isDirectory(workspaceRoot.resolve(".git"))) {
            return new ToolResult(
                    toolCallId,
                    toolName,
                    "error",
                    "not a git repository (no .git directory)",
                    false,
                    null,
                    ErrorInfo.of(ErrorCodes.TOOL_FAILED, "not a git repository", false));
        }
        try {
            String[] command = new String[args.length + 1];
            command[0] = "git";
            System.arraycopy(args, 0, command, 1, args.length);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workspaceRoot.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return error(toolCallId, toolName, "git command timed out");
            }
            String output;
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().reduce((a, b) -> a + "\n" + b).orElse("");
            }
            if (output.length() > MAX_OUTPUT) {
                output = output.substring(0, MAX_OUTPUT) + "\n...[truncated]";
            }
            if (output.isBlank() && process.exitValue() == 0) {
                output = "(no output)";
            }
            String status = process.exitValue() == 0 ? "ok" : "error";
            ErrorInfo err = process.exitValue() == 0
                    ? null
                    : ErrorInfo.of(ErrorCodes.TOOL_FAILED, "git exit " + process.exitValue(), false);
            return new ToolResult(toolCallId, toolName, status, output, output.length() >= MAX_OUTPUT, null, err);
        } catch (Exception e) {
            return error(toolCallId, toolName, e.getMessage());
        }
    }

    private static ToolResult error(String toolCallId, String toolName, String message) {
        return new ToolResult(
                toolCallId,
                toolName,
                "error",
                "",
                false,
                null,
                ErrorInfo.of(ErrorCodes.TOOL_FAILED, message, false));
    }
}
