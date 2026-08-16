package com.anvil.tools;

import com.anvil.protocol.ErrorCodes;
import com.anvil.protocol.ErrorInfo;
import com.anvil.protocol.ToolResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Collect compile/test diagnostics by running Maven in the workspace. */
public final class DiagnosticsTool {

    private static final int MAX_OUTPUT = 48_000;
    private static final int MAX_DIAGNOSTICS = 40;

    private DiagnosticsTool() {}

    public static ToolResult collect(Path workspaceRoot, String toolCallId, String scope, long timeoutMs) {
        String normalized = scope == null || scope.isBlank() ? "compile" : scope.trim().toLowerCase();
        String command =
                switch (normalized) {
                    case "test" -> "mvn -q test -am";
                    case "compile", "build" -> "mvn -q -DskipTests compile";
                    default -> "mvn -q -DskipTests compile";
                };
        if (!Files.exists(workspaceRoot.resolve("pom.xml"))) {
            return new ToolResult(
                    toolCallId,
                    "diagnostics.collect",
                    "error",
                    "no pom.xml in workspace root — not a Maven project",
                    false,
                    null,
                    ErrorInfo.of(ErrorCodes.TOOL_FAILED, "not a maven project", false));
        }

        ProcessRunner.Result run = ProcessRunner.run(workspaceRoot, command, timeoutMs);
        String output = truncate(run.output());
        List<DiagnosticParser.Diagnostic> diagnostics = DiagnosticParser.parse(output);
        String formatted = DiagnosticParser.format(diagnostics, MAX_DIAGNOSTICS);

        StringBuilder body = new StringBuilder();
        body.append("command: ").append(command).append('\n');
        body.append("exit: ").append(run.exitCode()).append('\n');
        if (run.timedOut()) {
            body.append("status: timed out\n");
        }
        body.append("diagnostics (").append(diagnostics.size()).append("):\n");
        body.append(formatted);
        if (!diagnostics.isEmpty()) {
            body.append("\n\nraw tail:\n").append(tail(output, 2000));
        } else if (!output.isBlank()) {
            body.append("\n\noutput tail:\n").append(tail(output, 4000));
        }

        String status = run.exitCode() == 0 && diagnostics.stream().noneMatch(d -> "ERROR".equals(d.severity()))
                ? "ok"
                : "error";
        ErrorInfo error = "ok".equals(status)
                ? null
                : ErrorInfo.of(ErrorCodes.TOOL_FAILED, "diagnostics found errors (exit " + run.exitCode() + ")", false);
        return new ToolResult(toolCallId, "diagnostics.collect", status, body.toString(), output.length() >= MAX_OUTPUT, null, error);
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_OUTPUT ? text : text.substring(0, MAX_OUTPUT) + "\n...[truncated]";
    }

    private static String tail(String text, int max) {
        if (text == null || text.length() <= max) {
            return text == null ? "" : text;
        }
        return "...\n" + text.substring(text.length() - max);
    }
}
