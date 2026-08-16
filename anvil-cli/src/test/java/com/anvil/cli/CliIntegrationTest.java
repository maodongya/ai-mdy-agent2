package com.anvil.cli;

import com.anvil.cli.client.AnvilClient;
import com.anvil.server.AnvilApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = AnvilApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CliIntegrationTest {

    @LocalServerPort
    int port;

    @Test
    void threadCreateStartAndAttach() throws Exception {
        Path workspace = repoRoot().resolve("fixtures/repos/sample-lib");
        AnvilClient client = new AnvilClient("http://127.0.0.1:" + port);

        Map<String, Object> thread = client.createThread(workspace.toString());
        String threadId = String.valueOf(thread.get("thread_id"));

        Map<String, Object> run =
                client.startRun(threadId, "agent", "scripted:read-add", "read Add.java");
        String runId = String.valueOf(run.get("run_id"));

        List<String> types = new ArrayList<>();
        client.attachRun(runId, 0, event -> types.add(event.path("type").asText()));

        assertTrue(types.contains("run.started"));
        assertTrue(types.contains("tool.completed"));
        assertTrue(types.contains("run.completed"));
    }

    @Test
    void picocliThreadCreateAgainstServer() {
        Path workspace = repoRoot().resolve("fixtures/repos/sample-lib");
        int exit = new picocli.CommandLine(new AnvilCli())
                .execute(
                        "--server",
                        "http://127.0.0.1:" + port,
                        "thread",
                        "create",
                        "--cwd",
                        workspace.toString());
        assertTrue(exit == 0);
    }

    private static Path repoRoot() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        if (Files.isDirectory(cwd.resolve("fixtures"))) {
            return cwd;
        }
        if (Files.isDirectory(cwd.resolve("../fixtures"))) {
            return cwd.getParent();
        }
        return cwd.getParent();
    }
}
