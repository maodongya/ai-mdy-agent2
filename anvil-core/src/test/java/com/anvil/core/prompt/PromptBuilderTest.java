package com.anvil.core.prompt;

import com.anvil.protocol.Mode;
import com.anvil.protocol.SandboxTier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    @TempDir
    Path workspace;

    @Test
    void assemblyOrder() throws Exception {
        Files.writeString(workspace.resolve("AGENTS.md"), "# Agents\nRun tests");
        PromptBundle bundle = PromptBuilder.build(
                Mode.AGENT,
                workspace,
                SandboxTier.WORKSPACE_WRITE,
                "main",
                List.of(),
                "hello",
                List.of(Map.of("name", "fs.read"), Map.of("name", "fs.write")));

        assertTrue(bundle.instructions().contains("mode: agent"));
        assertEquals("fs.read", bundle.tools().get(0).get("name"));
        assertEquals("fs.write", bundle.tools().get(1).get("name"));
        assertEquals("user", bundle.input().get(bundle.input().size() - 1).get("role"));
        assertTrue(bundle.input().get(0).get("content").toString().contains("AGENTS"));
    }
}
