package com.anvil.sandbox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Ensures resolved paths stay inside workspace root (incl. symlink check). */
public final class PathGuard {

    private PathGuard() {}

    public static Path assertInsideWorkspace(Path workspaceRoot, String relativeOrAbsolute) {
        Path root = workspaceRoot.toAbsolutePath().normalize();
        Path candidate = Path.of(relativeOrAbsolute);
        if (!candidate.isAbsolute()) {
            candidate = root.resolve(relativeOrAbsolute);
        }
        candidate = candidate.normalize();

        if (!candidate.startsWith(root)) {
            throw new PathEscapeException("path escapes workspace: " + relativeOrAbsolute);
        }

        if (Files.exists(candidate)) {
            try {
                Path realRoot = root.toRealPath();
                Path realCandidate = candidate.toRealPath();
                if (!realCandidate.startsWith(realRoot)) {
                    throw new PathEscapeException("path escapes workspace via symlink: " + relativeOrAbsolute);
                }
                return realCandidate;
            } catch (IOException e) {
                throw new PathEscapeException("cannot resolve path: " + relativeOrAbsolute);
            }
        }

        Path parent = candidate.getParent();
        if (parent != null && Files.exists(parent)) {
            try {
                Path realRoot = root.toRealPath();
                Path realParent = parent.toRealPath();
                if (!realParent.startsWith(realRoot)) {
                    throw new PathEscapeException("path escapes workspace: " + relativeOrAbsolute);
                }
            } catch (IOException e) {
                throw new PathEscapeException("cannot resolve parent: " + relativeOrAbsolute);
            }
        }

        return candidate;
    }
}
