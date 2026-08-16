package com.anvil.core.golden;

import com.anvil.core.loop.LoopEngine;
import com.anvil.core.loop.LoopOptions;
import com.anvil.core.loop.LoopResult;
import com.anvil.core.loop.RunRequest;
import com.anvil.core.model.ScriptedModel;
import com.anvil.core.model.LlmRegistry;
import com.anvil.core.orchestrator.Orchestrator;
import com.anvil.core.tools.ToolCatalog;
import com.anvil.protocol.ApprovalDecision;
import com.anvil.protocol.Event;
import com.anvil.protocol.Mode;
import com.anvil.protocol.ProtocolJson;
import com.anvil.protocol.RunStatus;
import com.anvil.protocol.SandboxTier;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoldenEventTest {

    @ParameterizedTest
    @ValueSource(strings = {"read-add", "ask-deny-write", "agent-write-approve"})
    void scriptedRunMatchesGoldenEventTypes(String fixtureName) throws Exception {
        Path golden = repoRoot().resolve("fixtures/protocol/" + fixtureName + ".golden.json");
        JsonNode spec = ProtocolJson.mapper().readTree(Files.readString(golden));
        List<String> expected = new ArrayList<>();
        spec.get("event_types").forEach(n -> expected.add(n.asText()));

        Path workspace = workspaceFor(fixtureName);
        ScriptedModel model = new ScriptedModel(repoRoot().resolve("fixtures/models/" + fixtureName + ".jsonl"));
        Mode mode = modeFor(fixtureName);

        LoopResult result = LoopEngine.run(
                new RunRequest(
                        "thr_g",
                        "run_g",
                        mode,
                        "scripted:" + fixtureName,
                        "golden run",
                        workspace,
                        15,
                        5_000L,
                        30_000L),
                model,
                (id, preview, timeout) -> CompletableFuture.completedFuture(ApprovalDecision.ALLOW_ONCE),
                new LoopOptions(0, SandboxTier.WORKSPACE_WRITE, "main", ToolCatalog.builtinSchemas(mode), null),
                null);

        List<String> actual = result.events().stream()
                .map(Event::type)
                .filter(t -> !"message.delta".equals(t))
                .toList();
        assertEquals(expected, actual, "event type sequence mismatch for " + fixtureName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"read-add", "ask-deny-write"})
    void orchestratorParallelWorkersMatchGolden(String fixtureName) throws Exception {
        Path workspace = workspaceFor(fixtureName);
        Mode mode = modeFor(fixtureName);
        LoopOptions options =
                new LoopOptions(0, SandboxTier.WORKSPACE_WRITE, "main", ToolCatalog.builtinSchemas(mode), null);

        var workers = List.of(
                new Orchestrator.WorkerBrief("a", "thr_a", "run_a", mode, "scripted:" + fixtureName, "w1", workspace),
                new Orchestrator.WorkerBrief("b", "thr_b", "run_b", mode, "scripted:" + fixtureName, "w2", workspace));

        Orchestrator.FanOutResult fanOut = Orchestrator.fanOutParallel(
                workers,
                LlmRegistry.fromEnv(),
                repoRoot().resolve("fixtures/models"),
                (id, preview, timeout) -> CompletableFuture.completedFuture(ApprovalDecision.ALLOW_ONCE),
                options);

        assertEquals(RunStatus.SUCCEEDED, fanOut.aggregateStatus());
        assertEquals(2, fanOut.workers().size());
    }

    private static Mode modeFor(String fixtureName) {
        return switch (fixtureName) {
            case "ask-deny-write" -> Mode.ASK;
            default -> Mode.AGENT;
        };
    }

    private static Path workspaceFor(String fixtureName) throws Exception {
        if ("agent-write-approve".equals(fixtureName)) {
            return Files.createTempDirectory("anvil-golden-");
        }
        return repoRoot().resolve("fixtures/repos/sample-lib");
    }

    private static Path repoRoot() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        if (Files.isDirectory(cwd.resolve("fixtures"))) {
            return cwd;
        }
        return cwd.getParent();
    }
}
