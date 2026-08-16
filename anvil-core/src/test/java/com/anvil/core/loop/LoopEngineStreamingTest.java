package com.anvil.core.loop;

import com.anvil.core.model.ScriptedModel;
import com.anvil.protocol.ApprovalDecision;
import com.anvil.protocol.Event;
import com.anvil.protocol.Mode;
import com.anvil.protocol.RunStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopEngineStreamingTest {

    @Test
    void scriptedMessageEmitsDeltasBeforeCompleted() throws Exception {
        Path workspace = repoRoot().resolve("fixtures/repos/sample-lib");
        ScriptedModel model = new ScriptedModel(repoRoot().resolve("fixtures/models/read-add.jsonl"));

        LoopResult result = LoopEngine.run(
                new RunRequest(
                        "thr_stream",
                        "run_stream",
                        Mode.AGENT,
                        "scripted:read-add",
                        "read Add.java",
                        workspace,
                        10,
                        5_000L,
                        30_000L),
                model,
                (id, preview, timeout) -> CompletableFuture.completedFuture(ApprovalDecision.ALLOW_ONCE));

        assertTrue(result.status() == RunStatus.SUCCEEDED);
        List<String> types = result.events().stream().map(Event::type).toList();
        assertTrue(types.contains("message.delta"), types.toString());
        int deltaIdx = types.indexOf("message.delta");
        int completedIdx = types.lastIndexOf("message.completed");
        assertTrue(deltaIdx >= 0 && completedIdx > deltaIdx);

        String streamed = result.events().stream()
                .filter(e -> "message.delta".equals(e.type()))
                .map(e -> String.valueOf(e.payload().get("delta")))
                .reduce("", String::concat);
        String finalText = result.events().stream()
                .filter(e -> "message.completed".equals(e.type()))
                .map(e -> String.valueOf(e.payload().get("text")))
                .reduce((a, b) -> b)
                .orElse("");
        assertTrue(finalText.contains(streamed) || streamed.contains(finalText.trim()));
    }

    private static Path repoRoot() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        if (Files.isDirectory(cwd.resolve("fixtures"))) {
            return cwd;
        }
        return cwd.getParent();
    }
}
