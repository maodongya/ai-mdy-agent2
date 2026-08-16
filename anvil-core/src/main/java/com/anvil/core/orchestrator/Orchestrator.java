package com.anvil.core.orchestrator;

import com.anvil.core.loop.ApprovalGate;
import com.anvil.core.loop.LoopEngine;
import com.anvil.core.loop.LoopOptions;
import com.anvil.core.loop.LoopResult;
import com.anvil.core.loop.RunRequest;
import com.anvil.core.model.LlmRegistry;
import com.anvil.core.model.ModelProvider;
import com.anvil.core.model.ModelProviderFactory;
import com.anvil.core.model.LlmRegistry;
import com.anvil.protocol.Mode;
import com.anvil.protocol.RunStatus;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fan-out / fan-in orchestration for isolated worker runs (Manager-Worker pattern). */
public final class Orchestrator {

    private Orchestrator() {}

    public record WorkerBrief(
            String workerId,
            String threadId,
            String runId,
            Mode mode,
            String model,
            String prompt,
            Path workspace) {}

    public record WorkerResult(String workerId, LoopResult loopResult) {}

    public record FanOutResult(List<WorkerResult> workers, RunStatus aggregateStatus) {}

    public static FanOutResult fanOutSequential(
            List<WorkerBrief> workers,
            LlmRegistry llmDefaults,
            Path fixturesRoot,
            ApprovalGate approvalGate,
            LoopOptions options) throws Exception {
        List<WorkerResult> results = new ArrayList<>();
        RunStatus aggregate = RunStatus.SUCCEEDED;
        for (WorkerBrief brief : workers) {
            WorkerResult result = runOne(brief, llmDefaults, fixturesRoot, approvalGate, options);
            results.add(result);
            if (result.loopResult().status() != RunStatus.SUCCEEDED) {
                aggregate = RunStatus.FAILED;
            }
        }
        return new FanOutResult(List.copyOf(results), aggregate);
    }

    public static FanOutResult fanOutParallel(
            List<WorkerBrief> workers,
            LlmRegistry llmDefaults,
            Path fixturesRoot,
            ApprovalGate approvalGate,
            LoopOptions options) throws Exception {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<WorkerResult>> futures = new ArrayList<>();
            for (WorkerBrief brief : workers) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return runOne(brief, llmDefaults, fixturesRoot, approvalGate, options);
                            } catch (Exception e) {
                                throw new IllegalStateException(e);
                            }
                        },
                        pool));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            List<WorkerResult> results = futures.stream().map(CompletableFuture::join).toList();
            RunStatus aggregate = results.stream().allMatch(r -> r.loopResult().status() == RunStatus.SUCCEEDED)
                    ? RunStatus.SUCCEEDED
                    : RunStatus.FAILED;
            return new FanOutResult(results, aggregate);
        }
    }

    private static WorkerResult runOne(
            WorkerBrief brief,
            LlmRegistry llmDefaults,
            Path fixturesRoot,
            ApprovalGate approvalGate,
            LoopOptions options) throws Exception {
        ModelProvider provider = ModelProviderFactory.create(brief.model(), llmDefaults, fixturesRoot);
        RunRequest request = new RunRequest(
                brief.threadId(),
                brief.runId(),
                brief.mode(),
                brief.model(),
                brief.prompt(),
                brief.workspace(),
                20,
                1_800_000L,
                120_000L);
        LoopResult result = LoopEngine.run(request, provider, approvalGate, options, null);
        return new WorkerResult(brief.workerId(), result);
    }
}
