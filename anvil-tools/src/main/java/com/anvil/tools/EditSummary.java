package com.anvil.tools;

import java.nio.file.Files;
import java.nio.file.Path;

/** Line delta + Maven verify command inference for Phase 4 verify loop. */
public final class EditSummary {

    public record Delta(String path, int linesAdded, int linesRemoved) {}

    private EditSummary() {}

    public static Delta forReplace(String path, String oldText, String newText, int occurrences) {
        int oldLines = lineCount(oldText) * Math.max(1, occurrences);
        int newLines = lineCount(newText) * Math.max(1, occurrences);
        return new Delta(path, newLines, oldLines);
    }

    public static Delta forPatch(String path, String patch) {
        int added = 0;
        int removed = 0;
        if (patch != null) {
            for (String line : patch.split("\n")) {
                if (line.startsWith("+") && !line.startsWith("+++")) {
                    added++;
                } else if (line.startsWith("-") && !line.startsWith("---")) {
                    removed++;
                }
            }
        }
        return new Delta(path, added, removed);
    }

    public static Delta forWrite(String path, String previous, String next) {
        return new Delta(path, lineCount(next), lineCount(previous));
    }

    public static String inferMavenModule(Path workspaceRoot, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        String normalized = relativePath.replace('\\', '/');
        int slash = normalized.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        String candidate = normalized.substring(0, slash);
        if (Files.isDirectory(workspaceRoot.resolve(candidate))) {
            return candidate;
        }
        return null;
    }

    public static String inferVerifyCommand(Path workspaceRoot, String relativePath, String template) {
        if (template != null && !template.isBlank()) {
            String module = inferMavenModule(workspaceRoot, relativePath);
            return template.replace("{module}", module == null ? "." : module);
        }
        String module = inferMavenModule(workspaceRoot, relativePath);
        if (module != null && Files.exists(workspaceRoot.resolve(module).resolve("pom.xml"))) {
            return "mvn -q test -pl " + module + " -am";
        }
        if (Files.exists(workspaceRoot.resolve("pom.xml"))) {
            return "mvn -q test -am";
        }
        return null;
    }

    private static int lineCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) text.chars().filter(ch -> ch == '\n').count() + (text.endsWith("\n") ? 0 : 1);
    }
}
