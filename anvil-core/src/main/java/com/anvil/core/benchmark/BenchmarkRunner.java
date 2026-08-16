package com.anvil.core.benchmark;

import com.anvil.core.loop.LoopConfig;
import com.anvil.core.loop.LoopEngine;
import com.anvil.core.loop.LoopOptions;
import com.anvil.core.loop.LoopResult;
import com.anvil.core.loop.RunProfile;
import com.anvil.core.loop.RunRequest;
import com.anvil.core.loop.VerifyConfig;
import com.anvil.core.model.LlmRegistry;
import com.anvil.core.model.ModelProvider;
import com.anvil.core.model.ModelProviderFactory;
import com.anvil.core.model.ScriptedModel;
import com.anvil.core.tools.ToolCatalog;
import com.anvil.protocol.ApprovalDecision;
import com.anvil.protocol.Event;
import com.anvil.protocol.RunStatus;
import com.anvil.protocol.SandboxTier;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Runs benchmarks with scripted or live LLM providers. */
public final class BenchmarkRunner {

    public record CheckResult(String name, boolean passed, String detail) {}

    public record BenchmarkReport(
            String id,
            boolean passed,
            int score,
            int maxScore,
            RunStatus status,
            List<CheckResult> checks,
            List<String> eventTypes) {}

    private BenchmarkRunner() {}

    public static BenchmarkReport runAndScore(Path repoRoot, BenchmarkSpec spec) throws IOException {
        return runAndScore(repoRoot, spec, LlmRegistry.fromEnv());
    }

    public static BenchmarkReport runAndScore(Path repoRoot, BenchmarkSpec spec, LlmRegistry registry)
            throws IOException {
        Path workspace = prepareWorkspace(repoRoot, spec);
        try {
            ModelProvider provider = createProvider(repoRoot, spec, registry);
            VerifyConfig verify = spec.live()
                    ? VerifyConfig.forRun(VerifyConfig.defaults(), spec.modeEnum(), RunProfile.EXTENDED)
                    : VerifyConfig.disabled();
            LoopResult result = LoopEngine.run(
                    new RunRequest(
                            "bench_" + spec.id(),
                            "run_" + spec.id(),
                            spec.modeEnum(),
                            spec.model(),
                            spec.userMessage(),
                            workspace,
                            spec.maxSteps(),
                            5_000L,
                            120_000L),
                    provider,
                    (id, preview, timeout) -> CompletableFuture.completedFuture(ApprovalDecision.ALLOW_ONCE),
                    new LoopOptions(
                            RunProfile.EXTENDED.contextBudget(),
                            SandboxTier.WORKSPACE_WRITE,
                            "main",
                            ToolCatalog.builtinSchemas(spec.modeEnum()),
                            null,
                            RunProfile.EXTENDED,
                            true,
                            false,
                            verify,
                            LoopConfig.disabledParallel()),
                    null);
            return evaluate(spec, result, workspace);
        } finally {
            if (spec.usesTempWorkspace()) {
                deleteRecursively(workspace);
            }
        }
    }

    private static ModelProvider createProvider(Path repoRoot, BenchmarkSpec spec, LlmRegistry registry)
            throws IOException {
        if (spec.live()) {
            try {
                return ModelProviderFactory.create(spec.model(), registry, repoRoot.resolve("fixtures"));
            } catch (Exception e) {
                throw new IOException("live model provider failed: " + e.getMessage(), e);
            }
        }
        return new ScriptedModel(repoRoot.resolve(spec.model()));
    }

    static BenchmarkReport evaluate(BenchmarkSpec spec, LoopResult result, Path workspace) throws IOException {
        List<String> eventTypes = result.events().stream()
                .map(Event::type)
                .filter(t -> !"message.delta".equals(t))
                .toList();
        List<CheckResult> checks = new ArrayList<>();
        BenchmarkSpec.BenchmarkExpect expect = spec.expect();

        if (expect.status() != null) {
            String expected = expect.status().toLowerCase();
            String actual = result.status().wireValue();
            checks.add(new CheckResult(
                    "status", expected.equals(actual), "expected " + expected + ", got " + actual));
        }

        if (expect.eventTypes() != null && !expect.eventTypes().isEmpty()) {
            checks.add(new CheckResult(
                    "event_types", expect.eventTypes().equals(eventTypes), "sequence mismatch"));
        }

        if (expect.eventTypesContains() != null) {
            checks.add(new CheckResult(
                    "event_types_contains",
                    containsSubsequence(eventTypes, expect.eventTypesContains()),
                    "missing ordered subsequence " + expect.eventTypesContains()));
        }

        if (expect.forbidEventTypes() != null) {
            List<String> forbidden =
                    expect.forbidEventTypes().stream().filter(eventTypes::contains).toList();
            checks.add(new CheckResult(
                    "forbid_event_types",
                    forbidden.isEmpty(),
                    forbidden.isEmpty() ? "ok" : "forbidden events: " + forbidden));
        }

        int toolCalls = countToolCalls(result);
        if (expect.maxToolCalls() != null) {
            checks.add(new CheckResult(
                    "max_tool_calls",
                    toolCalls <= expect.maxToolCalls(),
                    toolCalls + " <= " + expect.maxToolCalls()));
        }

        if (expect.maxStepEvents() != null) {
            long steps = eventTypes.stream().filter("step.started"::equals).count();
            checks.add(new CheckResult(
                    "max_step_events",
                    steps <= expect.maxStepEvents(),
                    steps + " <= " + expect.maxStepEvents()));
        }

        if (expect.fileContains() != null) {
            for (var entry : expect.fileContains().entrySet()) {
                Path file = workspace.resolve(entry.getKey());
                String content = Files.isRegularFile(file) ? Files.readString(file) : "";
                checks.add(new CheckResult(
                        "file_contains:" + entry.getKey(),
                        content.contains(entry.getValue()),
                        entry.getKey() + " contains \"" + entry.getValue() + "\""));
            }
        }

        if (expect.fileEquals() != null) {
            for (var entry : expect.fileEquals().entrySet()) {
                Path file = workspace.resolve(entry.getKey());
                String content = Files.isRegularFile(file) ? Files.readString(file) : "";
                checks.add(new CheckResult(
                        "file_equals:" + entry.getKey(),
                        content.equals(entry.getValue()),
                        entry.getKey() + " exact match"));
            }
        }

        if (expect.fileExists() != null) {
            for (String rel : expect.fileExists()) {
                checks.add(new CheckResult(
                        "file_exists:" + rel,
                        Files.isRegularFile(workspace.resolve(rel)),
                        rel + " exists"));
            }
        }

        int maxScore = checks.size();
        int score = (int) checks.stream().filter(CheckResult::passed).count();
        boolean passed = checks.stream().allMatch(CheckResult::passed);
        return new BenchmarkReport(
                spec.id(), passed, score, maxScore, result.status(), List.copyOf(checks), List.copyOf(eventTypes));
    }

    private static int countToolCalls(LoopResult result) {
        for (int i = result.events().size() - 1; i >= 0; i--) {
            Event event = result.events().get(i);
            if ("run.completed".equals(event.type()) || "run.failed".equals(event.type())) {
                Object usage = event.payload().get("usage");
                if (usage instanceof Map<?, ?> map && map.get("tool_calls") instanceof Number n) {
                    return n.intValue();
                }
            }
        }
        return (int) result.events().stream().filter(e -> "tool.started".equals(e.type())).count();
    }

    private static boolean containsSubsequence(List<String> haystack, List<String> needle) {
        if (needle.isEmpty()) {
            return true;
        }
        int j = 0;
        for (String item : haystack) {
            if (item.equals(needle.get(j))) {
                j++;
                if (j == needle.size()) {
                    return true;
                }
            }
        }
        return false;
    }

    static Path prepareWorkspace(Path repoRoot, BenchmarkSpec spec) throws IOException {
        if (spec.usesTempWorkspace()) {
            Path temp = Files.createTempDirectory("anvil-bench-" + spec.id() + "-");
            if (spec.workspaceFrom() != null && !spec.workspaceFrom().isBlank()) {
                copyDirectory(repoRoot.resolve(spec.workspaceFrom()), temp);
            }
            return temp;
        }
        return repoRoot.resolve(spec.workspace());
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walk(root)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // best effort
                    }
                });
    }
}
