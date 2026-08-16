package com.anvil.tools;

import com.anvil.protocol.ErrorCodes;
import com.anvil.protocol.ErrorInfo;
import com.anvil.protocol.ToolResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class ShellTool {

    private static final int MAX_OUTPUT_CHARS = 256_000;

    private ShellTool() {}

    public static ToolResult exec(
            String toolCallId,
            Map<String, Object> args,
            Path workspaceRoot,
            long defaultTimeoutMs) {

        String command = String.valueOf(args.getOrDefault("command", ""));
        if (command.isBlank()) {
            return new ToolResult(
                    toolCallId,
                    "shell.exec",
                    "error",
                    "",
                    false,
                    null,
                    ErrorInfo.of(ErrorCodes.TOOL_ARG_INVALID, "command is required", false));
        }

        long timeoutMs = defaultTimeoutMs;
        Object t = args.get("timeout_ms");
        if (t instanceof Number n) {
            timeoutMs = n.longValue();
        }

        Path cwd = workspaceRoot;
        Object cwdArg = args.get("cwd");
        if (cwdArg != null && !String.valueOf(cwdArg).isBlank()) {
            cwd = workspaceRoot.resolve(String.valueOf(cwdArg)).normalize();
        }

        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ToolResult(
                        toolCallId,
                        "shell.exec",
                        "error",
                        "",
                        false,
                        null,
                        ErrorInfo.of(ErrorCodes.TOOL_TIMEOUT, "shell timeout", true));
            }

            String output;
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
            }

            boolean truncated = false;
            if (output.length() > MAX_OUTPUT_CHARS) {
                output = output.substring(0, MAX_OUTPUT_CHARS) + "\n...[truncated]";
                truncated = true;
            }

            int code = process.exitValue();
            String status = code == 0 ? "ok" : "error";
            ErrorInfo error = code == 0
                    ? null
                    : ErrorInfo.of(ErrorCodes.TOOL_FAILED, describeExit(code), false);

            if (output.isBlank() && code != 0) {
                output = "(exit " + code + ")";
            }

            return new ToolResult(toolCallId, "shell.exec", status, output, truncated, null, error);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ToolResult(
                    toolCallId,
                    "shell.exec",
                    "cancelled",
                    "",
                    false,
                    null,
                    ErrorInfo.of(ErrorCodes.CANCELLED, "interrupted", false));
        } catch (Exception e) {
            return new ToolResult(
                    toolCallId,
                    "shell.exec",
                    "error",
                    "",
                    false,
                    null,
                    ErrorInfo.of(ErrorCodes.TOOL_FAILED, e.getMessage(), false));
        }
    }

    private static String describeExit(int code) {
        return switch (code) {
            case 127 -> "exit 127 (command not found)";
            case 128 -> "exit 128 (invalid argument to exit)";
            case 129 -> "exit 129 (command not found or invalid flag — is git installed / is this a git repo?)";
            default -> "exit " + code;
        };
    }
}
