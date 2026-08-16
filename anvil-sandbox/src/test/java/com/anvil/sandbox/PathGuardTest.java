package com.anvil.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PathGuardTest {

    @TempDir
    Path workspace;

    @Test
    void allowsFileInside() throws Exception {
        Path file = workspace.resolve("src/App.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "class App {}");

        Path resolved = PathGuard.assertInsideWorkspace(workspace, "src/App.java");
        assertEquals(file.toRealPath(), resolved);
    }

    @Test
    void rejectsParentEscape() {
        assertThrows(PathEscapeException.class, () -> PathGuard.assertInsideWorkspace(workspace, "../outside.txt"));
    }

    @Test
    void allowsNewFileUnderWorkspace() {
        Path resolved = PathGuard.assertInsideWorkspace(workspace, "new/dir/file.txt");
        assertEquals(workspace.resolve("new/dir/file.txt").normalize(), resolved);
    }
}
