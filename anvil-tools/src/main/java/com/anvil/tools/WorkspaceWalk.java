package com.anvil.tools;

import com.anvil.sandbox.PathEscapeException;
import com.anvil.sandbox.PathGuard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Shared workspace file walk with standard ignore rules. */
public final class WorkspaceWalk {

    private static final Set<String> IGNORED_DIR_NAMES = Set.of(
            ".git", ".idea", ".gradle", ".mvn", ".vscode", ".anvil", "target", "build", "out", "dist", "node_modules",
            "__pycache__");

    private WorkspaceWalk() {}

    public static void forEachFile(Path workspaceRoot, Consumer<Path> consumer) throws IOException {
        Path root = workspaceRoot.toAbsolutePath().normalize();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                if (isUnderIgnored(root, file)) {
                    continue;
                }
                String rel = root.relativize(file).toString().replace('\\', '/');
                try {
                    PathGuard.assertInsideWorkspace(root, rel);
                } catch (PathEscapeException e) {
                    continue;
                }
                consumer.accept(file);
            }
        }
    }

    static boolean isUnderIgnored(Path root, Path file) {
        Path rel = root.relativize(file);
        for (Path part : rel) {
            if (IGNORED_DIR_NAMES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }
}
