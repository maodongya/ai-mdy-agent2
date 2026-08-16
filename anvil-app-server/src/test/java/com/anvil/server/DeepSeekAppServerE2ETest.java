package com.anvil.server;

import com.anvil.protocol.Mode;
import com.anvil.protocol.RunStatus;
import com.anvil.server.service.RunService;
import com.anvil.server.store.InMemoryStore;
import com.anvil.server.store.ThreadRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** App Server + DeepSeek live E2E (requires DEEPSEEK_API_KEY). */
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeepSeekAppServerE2ETest {

    private static final String MODEL =
            System.getenv().getOrDefault("ANVIL_E2E_MODEL", "deepseek-chat");

    @Autowired
    RunService runService;

    @Autowired
    InMemoryStore store;

    @Test
    void httpRunWithDeepSeekCompletes() throws Exception {
        Path workspace = repoRoot().resolve("fixtures/repos/sample-lib");
        ThreadRecord thread = runService.createThread(workspace);
        var run = runService.startRun(
                thread.threadId(),
                Mode.ASK,
                "deepseek:" + MODEL,
                "Read src/main/java/com/example/Add.java with fs.read and reply with the public class name only.");

        waitForTerminal(run.runId(), 120_000);
        RunStatus status = runService.liveStatus(run.runId());
        if (status != RunStatus.SUCCEEDED) {
            String detail = store.eventStore().allForRun(run.runId()).stream()
                    .filter(e -> "run.failed".equals(e.type()))
                    .map(e -> String.valueOf(e.payload()))
                    .findFirst()
                    .orElse("");
            String types = store.eventStore().allForRun(run.runId()).stream()
                    .map(e -> e.type())
                    .toList()
                    .toString();
            assertEquals(RunStatus.SUCCEEDED, status, detail + " events=" + types);
        }
        assertEquals(RunStatus.SUCCEEDED, status);

        var types = store.eventStore().allForRun(run.runId()).stream()
                .map(e -> e.type())
                .toList();
        assertTrue(types.contains("message.completed"), types.toString());
        assertTrue(types.contains("run.completed"), types.toString());
    }

    private void waitForTerminal(String runId, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            RunStatus status = runService.liveStatus(runId);
            if (status == RunStatus.SUCCEEDED || status == RunStatus.FAILED || status == RunStatus.CANCELLED) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(200);
        }
        throw new AssertionError("timeout waiting for run " + runId);
    }

    private static Path repoRoot() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        if (Files.isDirectory(cwd.resolve("fixtures"))) {
            return cwd;
        }
        return cwd.getParent();
    }
}
