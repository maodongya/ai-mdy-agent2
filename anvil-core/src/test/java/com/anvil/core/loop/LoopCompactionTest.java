package com.anvil.core.loop;

import com.anvil.core.model.ScriptedModel;
import com.anvil.core.tools.ToolCatalog;
import com.anvil.protocol.Event;
import com.anvil.protocol.Mode;
import com.anvil.protocol.RunStatus;
import com.anvil.protocol.SandboxTier;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopCompactionTest {

    @Test
    void emitsCompactionEventWhenForced() throws Exception {
        Path workspace = repoRoot().resolve("fixtures/repos/sample-lib");
        ScriptedModel model = new ScriptedModel(fixture("read-add.jsonl"));

        LoopOptions options = new LoopOptions(
                1,
                SandboxTier.WORKSPACE_WRITE,
                "main",
                ToolCatalog.builtinSchemas(Mode.AGENT),
                null);

        var result = LoopEngine.run(
                new RunRequest(
                        "thr_c",
                        "run_c",
                        Mode.AGENT,
                        "scripted:read-add",
                        "x".repeat(4000),
                        workspace,
                        10,
                        5_000L,
                        30_000L),
                model,
                (id, preview, timeout) -> CompletableFuture.completedFuture(
                        com.anvil.protocol.ApprovalDecision.ALLOW_ONCE),
                options,
                null);

        assertEquals(RunStatus.SUCCEEDED, result.status());
        List<String> types = result.events().stream().map(Event::type).toList();
        assertTrue(types.contains("context.compacted"));
    }

    private static Path repoRoot() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        if (java.nio.file.Files.isDirectory(cwd.resolve("fixtures"))) {
            return cwd;
        }
        return cwd.getParent();
    }

    private static Path fixture(String name) {
        return repoRoot().resolve("fixtures/models/" + name);
    }
}
