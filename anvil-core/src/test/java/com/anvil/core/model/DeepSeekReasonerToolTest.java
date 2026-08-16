package com.anvil.core.model;

import com.anvil.core.prompt.PromptBuilder;
import com.anvil.core.prompt.PromptBundle;
import com.anvil.core.tools.ToolCatalog;
import com.anvil.protocol.Mode;
import com.anvil.protocol.SandboxTier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class DeepSeekReasonerToolTest {

    @Test
    void reasonerSurvivesSecondTurnAfterToolCall() {
        Path workspace = repoRoot().resolve("fixtures/repos/sample-lib");
        OpenAiConfig config =
                OpenAiConfig.fromEnv("deepseek-reasoner", "DEEPSEEK_API_KEY", OpenAiConfig.DEEPSEEK_BASE_URL);
        OpenAiModelProvider model = new OpenAiModelProvider(config);

        List<Map<String, Object>> history = new ArrayList<>();
        history.add(Map.of(
                "role",
                "user",
                "content",
                "Call fs.read on src/main/java/com/example/Add.java, then reply with the public class name only."));

        PromptBundle prompt = PromptBuilder.build(
                Mode.ASK, workspace, SandboxTier.READ_ONLY, "main", history, null, ToolCatalog.builtinSchemas(Mode.ASK));

        var turn1 = model.nextTurn(new ModelTurnContext(history, prompt));
        assertTrue(turn1.isPresent() && turn1.get().hasToolCalls(), "expected tool call on turn 1");

        history.add(turn1.get().toHistoryMessage());
        history.add(Map.of(
                "role",
                "tool",
                "tool_call_id",
                turn1.get().toolCalls().get(0).id(),
                "content",
                "public class Add { }"));

        PromptBundle prompt2 = PromptBuilder.build(
                Mode.ASK, workspace, SandboxTier.READ_ONLY, "main", history, null, ToolCatalog.builtinSchemas(Mode.ASK));

        assertDoesNotThrow(() -> model.nextTurn(new ModelTurnContext(history, prompt2)));
    }

    private static Path repoRoot() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        if (Files.isDirectory(cwd.resolve("fixtures"))) {
            return cwd;
        }
        return cwd.getParent();
    }
}
