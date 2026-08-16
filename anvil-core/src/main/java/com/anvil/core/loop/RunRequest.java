package com.anvil.core.loop;

import com.anvil.protocol.Mode;
import com.anvil.tools.index.AtReferenceParser;
import com.anvil.tools.index.MavenModuleGraph;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    private static final int MAX_OPEN_FILES = 10;
    private static final int MAX_SELECTION_LINES = 120;
    private static final int MAX_BUFFER_CHARS = 4_000;
    private static final int AT_REF_PREVIEW_LINES = 40;

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
        return formatEditorContext(openFiles, focusFile, null, Map.of());
    }

    public static String formatEditorContext(List<String> openFiles, String focusFile, EditorSelection selection) {
        return formatEditorContext(openFiles, focusFile, selection, Map.of());
    }

    public static String formatEditorContext(
            List<String> openFiles,
            String focusFile,
            EditorSelection selection,
            Map<String, String> unsavedBuffers) {
        boolean hasFocus = focusFile != null && !focusFile.isBlank();
        boolean hasOpen = openFiles != null && !openFiles.isEmpty();
        boolean hasSelection = selection != null && !selection.isEmpty();
        Map<String, String> focusBuffers = focusOnlyBuffers(focusFile, unsavedBuffers);
        boolean hasBuffers = !focusBuffers.isEmpty();
        if (!hasFocus && !hasOpen && !hasSelection && !hasBuffers) {
            return "";
        }
        StringBuilder sb = new StringBuilder("<editor_context>\n");
        if (hasFocus) {
            sb.append("cursor_file: ").append(focusFile.trim()).append('\n');
            sb.append("focus_file: ").append(focusFile.trim()).append('\n');
        }
        if (hasOpen) {
            List<String> listed = new ArrayList<>();
            for (String path : openFiles) {
                if (path != null && !path.isBlank()) {
                    listed.add(path.trim());
                }
            }
            sb.append("open_files:\n");
            int shown = 0;
            for (String path : listed) {
                if (shown >= MAX_OPEN_FILES) {
                    sb.append("- …+").append(listed.size() - MAX_OPEN_FILES).append(" more\n");
                    break;
                }
                sb.append("- ").append(path).append('\n');
                shown++;
            }
        }
        if (hasSelection) {
            sb.append("selection: lines ")
                    .append(selection.startLine())
                    .append('-')
                    .append(selection.endLine())
                    .append('\n');
            sb.append("```\n").append(truncateSelection(selection.text())).append("\n```\n");
        }
        if (hasBuffers) {
            sb.append("unsaved_buffers:\n");
            for (Map.Entry<String, String> e : focusBuffers.entrySet()) {
                sb.append("- path: ").append(e.getKey().trim()).append('\n');
                sb.append("```\n").append(truncateBuffer(e.getValue())).append("\n```\n");
            }
        }
        sb.append("</editor_context>");
        return sb.toString();
    }

    /** Backward-compatible harness context without unsaved buffers. */
    public static String formatHarnessContext(
            Path workspace,
            String userMessage,
            List<String> openFiles,
            String focusFile,
            EditorSelection selection) {
        return formatHarnessContext(workspace, userMessage, openFiles, focusFile, selection, Map.of());
    }

    private static Map<String, String> focusOnlyBuffers(String focusFile, Map<String, String> unsavedBuffers) {
        if (unsavedBuffers == null || unsavedBuffers.isEmpty()) {
            return Map.of();
        }
        if (focusFile == null || focusFile.isBlank()) {
            return Map.of();
        }
        String focus = focusFile.trim();
        for (Map.Entry<String, String> e : unsavedBuffers.entrySet()) {
            if (e.getKey() != null && e.getKey().trim().equals(focus)) {
                return Map.of(focus, e.getValue() == null ? "" : e.getValue());
            }
        }
        return Map.of();
    }

    private static String truncateSelection(String content) {
        if (content == null) {
            return "";
        }
        String[] lines = content.split("\n", -1);
        if (lines.length <= MAX_SELECTION_LINES) {
            return content;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MAX_SELECTION_LINES; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        sb.append("\n...[selection truncated to ").append(MAX_SELECTION_LINES).append(" lines; use fs.read for more]");
        return sb.toString();
    }

    private static String truncateBuffer(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= MAX_BUFFER_CHARS) {
            return content;
        }
        return content.substring(0, MAX_BUFFER_CHARS) + "\n...[truncated unsaved buffer]";
    }

    /** Editor + Maven module graph + @ references + unsaved buffers (Phase 6/9/11). */
    public static String formatHarnessContext(
            Path workspace,
            String userMessage,
            List<String> openFiles,
            String focusFile,
            EditorSelection selection,
            Map<String, String> unsavedBuffers) {
        StringBuilder sb = new StringBuilder();
        String editor = formatEditorContext(openFiles, focusFile, selection, unsavedBuffers);
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
            sb.append(formatAtReferences(workspace, refs.resolvedPaths()));
        }
        return sb.toString().trim();
    }

    private static String formatAtReferences(Path workspace, List<String> paths) {
        StringBuilder sb = new StringBuilder("<at_references>\n");
        for (String path : paths) {
            sb.append("- ").append(path);
            String preview = lazyPreview(workspace, path);
            if (!preview.isBlank()) {
                sb.append(" (preview):\n```\n").append(preview).append("\n```");
            }
            sb.append('\n');
        }
        sb.append("</at_references>");
        return sb.toString();
    }

    private static String lazyPreview(Path workspace, String relativePath) {
        try {
            Path abs = workspace.resolve(relativePath).normalize();
            if (!Files.isRegularFile(abs)) {
                return "";
            }
            List<String> lines = Files.readAllLines(abs);
            if (lines.size() <= AT_REF_PREVIEW_LINES) {
                return String.join("\n", lines);
            }
            List<String> head = lines.subList(0, AT_REF_PREVIEW_LINES);
            return String.join("\n", head) + "\n… [use fs.read for full file]";
        } catch (Exception e) {
            return "";
        }
    }

    /** Strip @tokens and resolve referenced paths from user message. */
    public static AtReferenceParser.Result parseAtReferences(String userMessage, Path workspace) {
        return AtReferenceParser.parse(userMessage, workspace);
    }
}
