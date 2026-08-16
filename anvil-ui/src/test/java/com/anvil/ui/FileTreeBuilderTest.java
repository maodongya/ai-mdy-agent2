package com.anvil.ui;

import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileTreeBuilderTest {

    @Test
    void buildsNestedDirectories() {
        TreeItem<FileTreeBuilder.FileNode> root = FileTreeBuilder.build(List.of(
                "src/main/java/com/example/Add.java",
                "src/main/java/com/example/Main.java",
                "README.md"));

        assertEquals("workspace", root.getValue().label());
        assertEquals(2, root.getChildren().size());

        TreeItem<FileTreeBuilder.FileNode> src = root.getChildren().stream()
                .filter(c -> "src".equals(c.getValue().label()))
                .findFirst()
                .orElseThrow();
        assertNotNull(src);

        TreeItem<FileTreeBuilder.FileNode> readme = root.getChildren().stream()
                .filter(c -> "README.md".equals(c.getValue().label()))
                .findFirst()
                .orElseThrow();
        assertEquals("README.md", readme.getValue().fullPath());
    }

    @Test
    void buildsFromDirectoryNodesBeforeFilesArrive() {
        TreeItem<FileTreeBuilder.FileNode> root = FileTreeBuilder.build(
                List.of("anvil-ui/src", "anvil-ui/src/main", "anvil-ui/src/main/java"),
                Set.of(),
                "ai-mdy-agent2");

        TreeItem<FileTreeBuilder.FileNode> module = root.getChildren().stream()
                .filter(c -> "anvil-ui".equals(c.getValue().label()))
                .findFirst()
                .orElseThrow();
        assertTrue(module.getValue().directory());
        assertEquals(1, module.getChildren().size());
        assertEquals("src", module.getChildren().getFirst().getValue().label());
    }
}
