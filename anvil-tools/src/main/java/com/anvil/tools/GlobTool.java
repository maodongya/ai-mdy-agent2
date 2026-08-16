package com.anvil.tools;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

/** Simple glob match for grep path filter. */
final class GlobTool {

    private GlobTool() {}

    static boolean matches(String posixPath, String glob) {
        if (glob == null || glob.isBlank() || "**/*".equals(glob)) {
            return true;
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
        Path path = Path.of(posixPath);
        if (matcher.matches(path)) {
            return true;
        }
        if (matcher.matches(Path.of(path.getFileName().toString()))) {
            return true;
        }
        // Match root-level files for patterns like **/*.java
        if (glob.contains("/") && !posixPath.contains("/")) {
            PathMatcher fileNameMatcher = FileSystems.getDefault().getPathMatcher("glob:" + glob.replace("**/", ""));
            return fileNameMatcher.matches(Path.of(posixPath));
        }
        return false;
    }
}
