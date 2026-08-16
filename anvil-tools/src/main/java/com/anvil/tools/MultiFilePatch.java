package com.anvil.tools;

import com.anvil.sandbox.PathGuard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Multi-file unified diff parser and atomic applier (Phase 7.2). */
public final class MultiFilePatch {

    private MultiFilePatch() {}

    public record FilePatch(String path, String patchBody) {}

    public static boolean isMultiFile(String patch) {
        return patch != null && patch.contains("--- ") && patch.contains("+++ ");
    }

    /** Parse a multi-file unified diff into per-file patch bodies. */
    public static List<FilePatch> parse(String patch) {
        if (patch == null || patch.isBlank() || !isMultiFile(patch)) {
            return List.of();
        }
        String normalized = patch.replace("\r\n", "\n");
        Map<String, StringBuilder> bodies = new LinkedHashMap<>();
        String currentPath = null;

        for (String line : normalized.split("\n", -1)) {
            if (line.startsWith("+++ ")) {
                String raw = line.substring(4).trim();
                int tab = raw.indexOf('\t');
                if (tab > 0) {
                    raw = raw.substring(0, tab);
                }
                if (!"/dev/null".equals(raw)) {
                    currentPath = normalizePath(raw);
                    bodies.putIfAbsent(currentPath, new StringBuilder());
                }
            }
            if (currentPath != null) {
                bodies.get(currentPath).append(line).append('\n');
            }
        }

        List<FilePatch> out = new ArrayList<>();
        for (var e : bodies.entrySet()) {
            String body = e.getValue().toString().trim();
            if (!body.isBlank()) {
                out.add(new FilePatch(e.getKey(), body));
            }
        }
        return out;
    }

    /**
     * Apply patch to multiple files atomically. Rolls back all touched files on any failure.
     *
     * @return null on success, or error message
     */
    public static String applyAll(Path workspaceRoot, List<FilePatch> patches) {
        Map<Path, String> backups = new LinkedHashMap<>();
        try {
            for (FilePatch fp : patches) {
                Path abs = PathGuard.assertInsideWorkspace(workspaceRoot, fp.path());
                backups.putIfAbsent(abs, Files.isRegularFile(abs) ? Files.readString(abs) : "");
                String updated = EditTools.applyUnifiedPatch(backups.get(abs), fp.patchBody());
                if (updated == null) {
                    rollback(backups);
                    return "patch did not apply cleanly for " + fp.path();
                }
                Files.createDirectories(abs.getParent() != null ? abs.getParent() : workspaceRoot);
                Files.writeString(abs, updated);
            }
            return null;
        } catch (Exception e) {
            rollback(backups);
            return e.getMessage();
        }
    }

    private static void rollback(Map<Path, String> backups) {
        for (var e : backups.entrySet()) {
            try {
                Files.writeString(e.getKey(), e.getValue());
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    private static String normalizePath(String raw) {
        String p = raw.replace('\\', '/');
        if (p.startsWith("a/") || p.startsWith("b/")) {
            p = p.substring(2);
        }
        if (p.startsWith("./")) {
            p = p.substring(2);
        }
        return p;
    }
}
