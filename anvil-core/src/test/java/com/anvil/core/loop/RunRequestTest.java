package com.anvil.core.loop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RunRequestTest {

    @TempDir
    Path workspace;

    @Test
    void formatsEditorContext() {
        String block = RunRequest.formatEditorContext(List.of("a.java", "b.java"), "b.java");
        assertTrue(block.contains("cursor_file: b.java"));
        assertTrue(block.contains("focus_file: b.java"));
        assertTrue(block.contains("- a.java"));
        assertTrue(block.contains("- b.java"));
    }

    @Test
    void formatsEditorSelection() {
        String block = RunRequest.formatEditorContext(
                List.of("Foo.java"), "Foo.java", new EditorSelection(10, 12, "selected code"));
        assertTrue(block.contains("selection: lines 10-12"));
        assertTrue(block.contains("selected code"));
    }

    @Test
    void formatsUnsavedBuffers() {
        String block = RunRequest.formatEditorContext(
                List.of("Foo.java"),
                "Foo.java",
                null,
                Map.of("Foo.java", "unsaved content"));
        assertTrue(block.contains("unsaved_buffers"));
        assertTrue(block.contains("unsaved content"));
    }

    @Test
    void formatsHarnessContextWithModuleGraphAndAtRefs() throws Exception {
        Files.writeString(
                workspace.resolve("pom.xml"),
                "<project><artifactId>demo</artifactId></project>");
        Path src = workspace.resolve("Foo.java");
        Files.writeString(src, "class Foo {}");

        String block = RunRequest.formatHarnessContext(
                workspace, "fix @Foo.java", List.of("Foo.java"), "Foo.java", null);

        assertTrue(block.contains("<module_graph>"));
        assertTrue(block.contains("<at_references>"));
        assertTrue(block.contains("Foo.java"));
    }
}
