package com.anvil.core.model;

import com.anvil.core.loop.LoopEngine;
import com.anvil.core.loop.LoopOptions;
import com.anvil.core.loop.LoopResult;
import com.anvil.core.loop.RunRequest;
import com.anvil.core.tools.ToolCatalog;
import com.anvil.protocol.ApprovalDecision;
import com.anvil.protocol.Event;
import com.anvil.protocol.Mode;
import com.anvil.protocol.RunStatus;
import com.anvil.protocol.SandboxTier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live OpenAI E2E — skipped unless {@code OPENAI_API_KEY} is set.
 *
 * <pre>
 * export OPENAI_API_KEY=sk-...
 * mvn -pl anvil-core -Dtest=OpenAiE2ETest test
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiE2ETest {

    private static final String MODEL = System.getenv().getOrDefault("ANVIL_E2E_MODEL", "gpt-4o-mini");

    @Test
    void askModeReadsFileAndCompletes() {
        Path workspace = repoRoot().resolve("fixtures/repos/sample-lib");
        OpenAiConfig config = OpenAiConfig.fromEnv(MODEL, "OPENAI_API_KEY");
        OpenAiModelProvider model = new OpenAiModelProvider(config);

        LoopOptions options = new LoopOptions(
                120_000,
                SandboxTier.READ_ONLY,
                "main",
                ToolCatalog.builtinSchemas(Mode.ASK),
                null);

        LoopResult result = LoopEngine.run(
                new RunRequest(
                        "thr_oai",
                        "run_oai",
                        Mode.ASK,
                        "openai:" + MODEL,
                        "Use fs.read on path src/main/java/com/example/Add.java then reply with the public class name only.",
                        workspace,
                        8,
                        60_000L,
                        30_000L),
                model,
                (id, preview, timeout) -> CompletableFuture.completedFuture(ApprovalDecision.ALLOW_ONCE),
                options,
                null);

        assertEquals(RunStatus.SUCCEEDED, result.status(), "events: " + eventTypes(result));
        List<String> types = eventTypes(result);
        assertTrue(types.contains("tool.completed"), types.toString());
        assertTrue(types.contains("message.completed"), types.toString());

        String assistantText = result.events().stream()
                .filter(e -> "message.completed".equals(e.type()))
                .map(e -> String.valueOf(e.payload().getOrDefault("text", "")))
                .findFirst()
                .orElse("");
        assertTrue(
                assistantText.toLowerCase().contains("add"),
                "expected class name in assistant reply, got: " + assistantText);
    }

    private static List<String> eventTypes(LoopResult result) {
        return result.events().stream().map(Event::type).toList();
    }

    private static Path repoRoot() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        if (Files.isDirectory(cwd.resolve("fixtures"))) {
            return cwd;
        }
        return cwd.getParent();
    }
}
