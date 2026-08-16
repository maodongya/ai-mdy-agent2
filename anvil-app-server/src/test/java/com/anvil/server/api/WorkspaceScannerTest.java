package com.anvil.server.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceScannerTest {

    @Test
    void includesDeepMavenSourceFiles() throws Exception {
        Path root = repoRoot().resolve("fixtures/repos/sample-lib");
        List<Map<String, Object>> nodes = WorkspaceScanner.scan(root);

        List<String> files = nodes.stream()
                .filter(n -> "file".equals(n.get("type")))
                .map(n -> String.valueOf(n.get("path")))
                .collect(Collectors.toList());

        assertTrue(files.contains("Hello.java"), "files=" + files);
        assertTrue(files.contains("src/main/java/com/example/Add.java"), "files=" + files);
    }

    @Test
    void includesMultiModuleSourceFiles() throws Exception {
        Path root = repoRoot();
        if (!root.resolve("anvil-ui/src/main/java/com/anvil/ui/AnvilUiApp.java").toFile().exists()) {
            return;
        }
        List<Map<String, Object>> nodes = WorkspaceScanner.scan(root);
        List<String> files = nodes.stream()
                .filter(n -> "file".equals(n.get("type")))
                .map(n -> String.valueOf(n.get("path")))
                .collect(Collectors.toList());

        assertTrue(
                files.contains("anvil-ui/src/main/java/com/anvil/ui/AnvilUiApp.java"),
                "files sample=" + files.stream().filter(f -> f.contains("AnvilUiApp")).toList());
    }

    private static Path repoRoot() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        if (cwd.resolve("fixtures/repos/sample-lib").toFile().exists()) {
            return cwd;
        }
        return cwd.getParent();
    }
}
