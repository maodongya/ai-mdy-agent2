package com.anvil.core.loop;

import com.anvil.core.model.ScriptedModel;
import com.anvil.protocol.ApprovalDecision;
import com.anvil.protocol.Event;
import com.anvil.protocol.Mode;
import com.anvil.protocol.RunStatus;
import com.anvil.tools.PlanTool;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopEngineTest {

    @Test
    void readAddGoldenEventTypes() throws Exception {
        Path workspace = repoRoot().resolve("fixtures/repos/sample-lib");
        ScriptedModel model = new ScriptedModel(fixture("read-add.jsonl"));

        LoopResult result = LoopEngine.run(
                new RunRequest(
                        "thr_1",
                        "run_1",
                        Mode.AGENT,
                        "scripted:read-add",
                        "read Add.java",
                        workspace,
                        10,
                        5_000L,
                        30_000L),
                model,
                (id, preview, timeout) -> CompletableFuture.completedFuture(ApprovalDecision.ALLOW_ONCE));

        assertEquals(RunStatus.SUCCEEDED, result.status());
        List<String> types = eventTypesWithoutDeltas(result);
        assertEquals(
                List.of(
                        "run.started",
                        "step.started",
                        "model.completed",
                        "tool.planned",
                        "tool.started",
                        "tool.completed",
                        "step.started",
                        "model.completed",
                        "message.completed",
                        "run.completed"),
                types);
    }

    @Test
    void askDeniesWriteThenCompletes() throws Exception {
        Path workspace = repoRoot().resolve("fixtures/repos/sample-lib");
        ScriptedModel model = new ScriptedModel(fixture("ask-deny-write.jsonl"));

        LoopResult result = LoopEngine.run(
                new RunRequest(
                        "thr_2",
                        "run_2",
                        Mode.ASK,
                        "scripted:ask-deny-write",
                        "try write",
                        workspace,
                        10,
                        5_000L,
                        30_000L),
                model,
                (id, preview, timeout) -> CompletableFuture.completedFuture(ApprovalDecision.ALLOW_ONCE));

        assertEquals(RunStatus.SUCCEEDED, result.status());
        List<String> types = eventTypesWithoutDeltas(result);
        assertEquals(
                List.of(
                        "run.started",
                        "step.started",
                        "model.completed",
                        "tool.planned",
                        "tool.failed",
                        "step.started",
                        "model.completed",
                        "message.completed",
                        "run.completed"),
                types);
    }

    @Test
    void agentWriteRequiresApproval() throws Exception {
        Path workspace = Files.createTempDirectory("anvil-agent-write");
        try {
            ScriptedModel model = new ScriptedModel(fixture("agent-write-approve.jsonl"));

            LoopResult result = LoopEngine.run(
                    new RunRequest(
                            "thr_3",
                            "run_3",
                            Mode.AGENT,
                            "scripted:agent-write-approve",
                            "write file",
                            workspace,
                            10,
                            5_000L,
                            30_000L),
                    model,
                    (id, preview, timeout) -> CompletableFuture.completedFuture(ApprovalDecision.ALLOW_ONCE));

            assertEquals(RunStatus.SUCCEEDED, result.status());
            List<String> types = eventTypesWithoutDeltas(result);
            assertTrue(types.contains("approval.required"));
            assertTrue(types.contains("approval.resolved"));
            assertTrue(Files.exists(workspace.resolve("approved.txt")));
        } finally {
            deleteRecursively(workspace);
        }
    }

    @Test
    void abortsAfterRepeatedWriteToolFailures() throws Exception {
        Path workspace = repoRoot().resolve("fixtures/repos/sample-lib-buggy");
        ScriptedModel model = new ScriptedModel(fixture("write-fail-loop.jsonl"));

        LoopResult result = LoopEngine.run(
                new RunRequest(
                        "thr_abort",
                        "run_abort",
                        Mode.AGENT,
                        "scripted:write-fail-loop",
                        "fix file",
                        workspace,
                        20,
                        5_000L,
                        30_000L),
                model,
                (id, preview, timeout) -> CompletableFuture.completedFuture(ApprovalDecision.ALLOW_ONCE),
                new LoopOptions(
                        RunProfile.EXTENDED.contextBudget(),
                        com.anvil.protocol.SandboxTier.WORKSPACE_WRITE,
                        "main",
                        com.anvil.core.tools.ToolCatalog.builtinSchemas(Mode.AGENT),
                        null,
                        RunProfile.EXTENDED,
                        true,
                        false,
                        VerifyConfig.disabled(),
                        LoopConfig.disabledParallel()),
                null);

        assertEquals(RunStatus.FAILED, result.status());
        List<String> types = eventTypesWithoutDeltas(result);
        assertTrue(types.contains("run.failed"));
        assertTrue(types.stream().filter("tool.failed"::equals).count() >= 5);
    }

    @Test
    void patchBugfixSucceedsWhenVerifyEnabledWithoutMavenProject() throws Exception {
        Path workspace = Files.createTempDirectory("anvil-patch-no-pom");
        try {
            Path source = repoRoot().resolve("fixtures/repos/sample-lib-buggy");
            copyDirectory(source, workspace);
            ScriptedModel model = new ScriptedModel(fixture("patch-add.jsonl"));

            LoopResult result = LoopEngine.run(
                    new RunRequest(
                            "thr_patch",
                            "run_patch",
                            Mode.AGENT,
                            "scripted:patch-add",
                            "fix add",
                            workspace,
                            12,
                            5_000L,
                            30_000L),
                    model,
                (id, preview, timeout) -> CompletableFuture.completedFuture(ApprovalDecision.ALLOW_ONCE),
                    new LoopOptions(
                            RunProfile.EXTENDED.contextBudget(),
                            com.anvil.protocol.SandboxTier.WORKSPACE_WRITE,
                            "main",
                            com.anvil.core.tools.ToolCatalog.builtinSchemas(Mode.AGENT),
                            null,
                            RunProfile.EXTENDED,
                            true,
                            false,
                            VerifyConfig.defaults(),
                            LoopConfig.disabledParallel()),
                    null);

            assertEquals(RunStatus.SUCCEEDED, result.status());
            assertTrue(Files.readString(workspace.resolve("src/main/java/com/example/Add.java")).contains("return a + b"));
            List<String> types = eventTypesWithoutDeltas(result);
            assertTrue(types.contains("run.completed"));
            assertTrue(types.stream().noneMatch("diagnostics.auto.failed"::equals));
        } finally {
            deleteRecursively(workspace);
        }
    }

    @Test
    void planModeUpdatesPlanFile() throws Exception {
        Path workspace = Files.createTempDirectory("anvil-plan");
        try {
            ScriptedModel model = new ScriptedModel(fixture("plan-update.jsonl"));

            LoopResult result = LoopEngine.run(
                    new RunRequest(
                            "thr_4",
                            "run_4",
                            Mode.PLAN,
                            "scripted:plan-update",
                            "update plan",
                            workspace,
                            10,
                            5_000L,
                            30_000L),
                    model,
                    (id, preview, timeout) -> CompletableFuture.completedFuture(ApprovalDecision.ALLOW_ONCE));

            assertEquals(RunStatus.SUCCEEDED, result.status());
            Path planFile = workspace.resolve(PlanTool.PLAN_PATH);
            assertTrue(Files.exists(planFile));
            assertTrue(Files.readString(planFile).contains("# Plan"));
        } finally {
            deleteRecursively(workspace);
        }
    }

    private static List<String> eventTypesWithoutDeltas(LoopResult result) {
        return result.events().stream()
                .map(Event::type)
                .filter(t -> !"message.delta".equals(t))
                .toList();
    }

    private static Path repoRoot() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        if (Files.isDirectory(cwd.resolve("fixtures"))) {
            return cwd;
        }
        return cwd.getParent();
    }

    private static Path fixture(String name) {
        return repoRoot().resolve("fixtures/models/" + name);
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // best effort cleanup
                }
            });
        }
    }

    private static void copyDirectory(Path source, Path target) throws Exception {
        try (var walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                Path dest = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest);
                }
            }
        }
    }
}
