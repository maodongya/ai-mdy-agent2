package com.anvil.core.loop;

import com.anvil.protocol.Mode;
import com.anvil.tools.index.AtReferenceParser;
import com.anvil.tools.index.MavenModuleGraph;

import java.nio.file.Path;
import java.util.List;

public record RunRequest(
        String threadId,
        String runId,
        Mode mode,
        String model,
        String userMessage,
        Path workspaceRoot,
        int maxSteps,
        long approvalTimeoutMs,
        long shellTimeoutMs,
        String editorContext) {

    public RunRequest {
        if (maxSteps <= 0) {
            maxSteps = 40;
        }
        if (approvalTimeoutMs <= 0) {
            approvalTimeoutMs = 1_800_000L;
        }
        if (shellTimeoutMs <= 0) {
            shellTimeoutMs = 120_000L;
        }
        workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        editorContext = editorContext == null ? "" : editorContext.trim();
    }

    /** Backward-compatible constructor without editor context. */
    public RunRequest(
            String threadId,
            String runId,
            Mode mode,
            String model,
            String userMessage,
            Path workspaceRoot,
            int maxSteps,
            long approvalTimeoutMs,
            long shellTimeoutMs) {
        this(threadId, runId, mode, model, userMessage, workspaceRoot, maxSteps, approvalTimeoutMs, shellTimeoutMs, "");
    }

    public static String formatEditorContext(List<String> openFiles, String focusFile) {
        return formatEditorContext(openFiles, focusFile, null);
    }

    public static String formatEditorContext(List<String> openFiles, String focusFile, EditorSelection selection) {
        boolean hasFocus = focusFile != null && !focusFile.isBlank();
        boolean hasOpen = openFiles != null && !openFiles.isEmpty();
        boolean hasSelection = selection != null && !selection.isEmpty();
        if (!hasFocus && !hasOpen && !hasSelection) {
            return "";
        }
        StringBuilder sb = new StringBuilder("<editor_context>\n");
        if (hasFocus) {
            sb.append("cursor_file: ").append(focusFile.trim()).append('\n');
            sb.append("focus_file: ").append(focusFile.trim()).append('\n');
        }
        if (hasOpen) {
            sb.append("open_files:\n");
            for (String path : openFiles) {
                if (path != null && !path.isBlank()) {
                    sb.append("- ").append(path.trim()).append('\n');
                }
            }
        }
        if (hasSelection) {
            sb.append("selection: lines ")
                    .append(selection.startLine())
                    .append('-')
                    .append(selection.endLine())
                    .append('\n');
            sb.append("```\n").append(selection.text()).append("\n```\n");
        }
        sb.append("</editor_context>");
        return sb.toString();
    }

    /** Editor + Maven module graph + @ references for harness context (Phase 6). */
    public static String formatHarnessContext(
            Path workspace,
            String userMessage,
            List<String> openFiles,
            String focusFile,
            EditorSelection selection) {
        StringBuilder sb = new StringBuilder();
        String editor = formatEditorContext(openFiles, focusFile, selection);
        if (!editor.isBlank()) {
            sb.append(editor);
        }
        String modules = MavenModuleGraph.format(workspace);
        if (!modules.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(modules);
        }
        AtReferenceParser.Result refs = AtReferenceParser.parse(userMessage, workspace);
        if (!refs.resolvedPaths().isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append("<at_references>\n");
            for (String path : refs.resolvedPaths()) {
                sb.append("- ").append(path).append('\n');
            }
            sb.append("</at_references>");
        }
        return sb.toString().trim();
    }

    /** Strip @tokens and resolve referenced paths from user message. */
    public static AtReferenceParser.Result parseAtReferences(String userMessage, Path workspace) {
        return AtReferenceParser.parse(userMessage, workspace);
    }
}
