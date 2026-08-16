package com.anvil.tools;

import com.anvil.protocol.ToolResult;
import com.anvil.tools.index.IndexService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CodebaseSearchToolTest {

    @Test
    void semanticSearchFindsConnectMethod(@TempDir Path workspace) throws Exception {
        Path src = workspace.resolve("src/AnvilClient.java");
        Files.createDirectories(src.getParent());
        Files.writeString(
                src,
                """
                public class AnvilClient {
                    public void connect() {
                        // open connection
                    }
                }
                """);
        IndexService.invalidate(workspace);
        IndexService.warm(workspace);

        ToolResult result = CodebaseSearchTool.search(workspace, "tc1", "connect", 10);
        assertTrue(result.content().contains("AnvilClient.java"));
    }
}
