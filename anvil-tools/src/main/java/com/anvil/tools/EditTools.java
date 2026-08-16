package com.anvil.tools;

import com.anvil.protocol.ErrorCodes;
import com.anvil.protocol.ErrorInfo;
import com.anvil.protocol.ToolResult;
import com.anvil.sandbox.PathEscapeException;
import com.anvil.sandbox.PathGuard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Structured file edits (prefer over full fs.write for existing files). */
public final class EditTools {

    private static final int MAX_WRITE_LINES_HINT = 300;

    private EditTools() {}

    public static ToolResult searchReplace(
            FsTools fs,
            String toolCallId,
            String path,
            String oldString,
            String newString,
            boolean replaceAll) {

        if (path == null || path.isBlank()) {
            return argError(toolCallId, "search_replace", "path is required");
        }
        if (oldString == null || oldString.isEmpty()) {
            return argError(toolCallId, "search_replace", "old_string is required and must be non-empty");
        }
        if (newString == null) {
            newString = "";
        }
        if (oldString.equals(newString)) {
            return argError(toolCallId, "search_replace", "old_string and new_string are identical");
        }

        try {
            Path abs = PathGuard.assertInsideWorkspace(fs.workspaceRoot(), path);
            if (!Files.isRegularFile(abs)) {
                return error(toolCallId, "search_replace", ErrorCodes.TOOL_FAILED, "not a file: " + path);
            }
            String content = Files.readString(abs);
            int count = countOccurrences(content, oldString);
            if (count == 0) {
                return error(
                        toolCallId,
                        "search_replace",
                        ErrorCodes.TOOL_FAILED,
                        "old_string not found in " + path);
            }
            if (!replaceAll && count > 1) {
                return error(
                        toolCallId,
                        "search_replace",
                        ErrorCodes.TOOL_FAILED,
                        "old_string matches " + count + " times; set replace_all=true or use a unique old_string");
            }
            String updated = replaceAll ? content.replace(oldString, newString) : content.replaceFirst(oldString, newString);
            Files.writeString(abs, updated);
            int replaced = replaceAll ? count : 1;
            return ToolResult.ok(
                    toolCallId,
                    "search_replace",
                    "replaced " + replaced + " occurrence(s) in " + path);
        } catch (PathEscapeException e) {
            return denied(toolCallId, "search_replace", e.getMessage());
        } catch (IOException e) {
            return error(toolCallId, "search_replace", ErrorCodes.TOOL_FAILED, e.getMessage());
        }
    }

    /**
     * Apply a unified diff to a single file. Patch may include multiple hunks for one path.
     * Lines must use Unix newlines.
     */
    public static ToolResult applyPatch(FsTools fs, String toolCallId, String path, String patch) {
        if (path == null || path.isBlank()) {
            return argError(toolCallId, "apply_patch", "path is required");
        }
        if (patch == null || patch.isBlank()) {
            return argError(toolCallId, "apply_patch", "patch is required");
        }

        try {
            Path abs = PathGuard.assertInsideWorkspace(fs.workspaceRoot(), path);
            if (!Files.isRegularFile(abs)) {
                return error(toolCallId, "apply_patch", ErrorCodes.TOOL_FAILED, "not a file: " + path);
            }
            String content = Files.readString(abs);
            String updated = applyUnifiedPatch(content, patch);
            if (updated == null) {
                return error(toolCallId, "apply_patch", ErrorCodes.TOOL_FAILED, "patch did not apply cleanly");
            }
            Files.writeString(abs, updated);
            return ToolResult.ok(toolCallId, "apply_patch", "patched " + path);
        } catch (PathEscapeException e) {
            return denied(toolCallId, "apply_patch", e.getMessage());
        } catch (IOException e) {
            return error(toolCallId, "apply_patch", ErrorCodes.TOOL_FAILED, e.getMessage());
        }
    }

    /** Warn in tool description — large files should not use fs.write. */
    public static int maxWriteLinesHint() {
        return MAX_WRITE_LINES_HINT;
    }

    static String applyUnifiedPatch(String original, String patch) {
        List<String> lines = new ArrayList<>(List.of(original.split("\n", -1)));
        if (lines.size() > 1 && lines.get(lines.size() - 1).isEmpty() && !original.endsWith("\n")) {
            lines.remove(lines.size() - 1);
        }

        String[] patchLines = patch.replace("\r\n", "\n").stripTrailing().split("\n");
        int i = 0;
        while (i < patchLines.length) {
            if (!patchLines[i].startsWith("@@")) {
                i++;
                continue;
            }
            Hunk hunk = parseHunkHeader(patchLines[i]);
            if (hunk == null) {
                return null;
            }
            i++;
            List<String> oldBlock = new ArrayList<>();
            List<String> newBlock = new ArrayList<>();
            while (i < patchLines.length && !patchLines[i].startsWith("@@")) {
                String line = patchLines[i];
                if (line.isEmpty()) {
                    i++;
                    continue;
                }
                if (line.charAt(0) == ' ') {
                    String text = line.substring(1);
                    oldBlock.add(text);
                    newBlock.add(text);
                } else if (line.charAt(0) == '-') {
                    oldBlock.add(line.substring(1));
                } else if (line.charAt(0) == '+') {
                    newBlock.add(line.substring(1));
                } else {
                    return null;
                }
                i++;
            }
            int startIdx = findBlock(lines, oldBlock, Math.max(0, hunk.oldStart - 1));
            if (startIdx < 0) {
                return null;
            }
            lines.subList(startIdx, startIdx + oldBlock.size()).clear();
            lines.addAll(startIdx, newBlock);
        }
        return String.join("\n", lines);
    }

    private static Hunk parseHunkHeader(String header) {
        // @@ -1,3 +1,4 @@
        try {
            String body = header.substring(2, header.lastIndexOf("@@")).trim();
            String[] parts = body.split(" ");
            String oldPart = parts[0].substring(1);
            int oldStart = Integer.parseInt(oldPart.split(",")[0]);
            return new Hunk(oldStart);
        } catch (Exception e) {
            return null;
        }
    }

    private static int findBlock(List<String> lines, List<String> block, int hint) {
        if (block.isEmpty()) {
            return hint;
        }
        for (int start = Math.max(0, hint - 3); start <= Math.min(lines.size() - block.size(), hint + 3); start++) {
            if (matchesBlock(lines, start, block)) {
                return start;
            }
        }
        for (int start = 0; start <= lines.size() - block.size(); start++) {
            if (matchesBlock(lines, start, block)) {
                return start;
            }
        }
        return -1;
    }

    private static boolean matchesBlock(List<String> lines, int start, List<String> block) {
        for (int i = 0; i < block.size(); i++) {
            if (!lines.get(start + i).equals(block.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private record Hunk(int oldStart) {}

    private static ToolResult argError(String toolCallId, String name, String message) {
        return new ToolResult(
                toolCallId,
                name,
                "error",
                "",
                false,
                null,
                ErrorInfo.of(ErrorCodes.TOOL_ARG_INVALID, message, false));
    }

    private static ToolResult denied(String toolCallId, String name, String message) {
        return new ToolResult(
                toolCallId,
                name,
                "denied",
                "",
                false,
                null,
                ErrorInfo.of(ErrorCodes.POLICY_DENIED, message, false));
    }

    private static ToolResult error(String toolCallId, String name, String code, String message) {
        return new ToolResult(toolCallId, name, "error", "", false, null, ErrorInfo.of(code, message, false));
    }
}
