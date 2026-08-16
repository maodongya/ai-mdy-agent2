package com.anvil.tools;

import com.anvil.protocol.ToolResult;
import com.anvil.tools.index.IndexService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolsSearchToolTest {

    @Test
    void listsImplementors(@TempDir Path workspace) throws Exception {
        Path dir = workspace.resolve("src");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Service.java"), "public interface Service {}");
        Files.writeString(
                dir.resolve("Impl.java"),
                """
                public class Impl implements Service {}
                """);
        IndexService.invalidate(workspace);
        IndexService.warm(workspace);

        ToolResult result = SymbolsSearchTool.search(workspace, "tc1", "Service", 20);
        assertTrue(result.content().contains("implementors"));
        assertTrue(result.content().contains("Impl"));
    }
}
