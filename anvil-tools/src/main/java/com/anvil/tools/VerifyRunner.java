package com.anvil.tools;

import com.anvil.protocol.ErrorCodes;
import com.anvil.protocol.ErrorInfo;
import com.anvil.protocol.ToolResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Auto-verify after writes (Maven test/compile). */
public final class VerifyRunner {

    private VerifyRunner() {}

    public static ToolResult run(Path workspaceRoot, String toolCallId, String command, long timeoutMs) {
        if (command == null || command.isBlank()) {
            return ToolResult.ok(toolCallId, "verify.auto", "verify skipped (no command inferred)");
        }
        if (!Files.exists(workspaceRoot.resolve("pom.xml"))) {
            return ToolResult.ok(toolCallId, "verify.auto", "verify skipped (no pom.xml at workspace root)");
        }

        ProcessRunner.Result run = ProcessRunner.run(workspaceRoot, command, timeoutMs);
        List<DiagnosticParser.Diagnostic> diagnostics = DiagnosticParser.parse(run.output());
        String diagText = DiagnosticParser.format(diagnostics, 30);

        StringBuilder body = new StringBuilder();
        body.append("command: ").append(command).append('\n');
        body.append("exit: ").append(run.exitCode()).append('\n');
        if (run.timedOut()) {
            body.append("timed out\n");
        }
        if (!diagText.isBlank() && !"no diagnostics parsed".equals(diagText)) {
            body.append("diagnostics:\n").append(diagText).append('\n');
        }
        if (run.exitCode() != 0) {
            body.append("\noutput tail:\n");
            body.append(tail(run.output(), 3000));
        }

        boolean ok = run.exitCode() == 0 && !run.timedOut();
        return new ToolResult(
                toolCallId,
                "verify.auto",
                ok ? "ok" : "error",
                body.toString().trim(),
                run.output().length() > 3000,
                null,
                ok ? null : ErrorInfo.of(ErrorCodes.TOOL_FAILED, "verify failed (exit " + run.exitCode() + ")", false));
    }

    private static String tail(String text, int max) {
        if (text == null || text.isBlank()) {
            return "(no output)";
        }
        return text.length() <= max ? text : "...\n" + text.substring(text.length() - max);
    }
}
