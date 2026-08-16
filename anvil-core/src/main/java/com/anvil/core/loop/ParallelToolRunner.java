package com.anvil.core.loop;

import com.anvil.core.model.ToolCallIntent;
import com.anvil.core.policy.Decision;
import com.anvil.core.tools.ToolExecutor;
import com.anvil.protocol.SideEffect;
import com.anvil.protocol.ToolResult;
import com.anvil.tools.ToolSideEffects;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Executes independent READ tools in parallel (Phase 5). */
final class ParallelToolRunner {

    private static final Executor EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private ParallelToolRunner() {}

    static boolean canParallelize(String toolName, Decision decision, boolean enabled) {
        if (!enabled || decision.type() != Decision.Type.ALLOW) {
            return false;
        }
        SideEffect effect = ToolSideEffects.forTool(toolName);
        return effect == SideEffect.READ;
    }

    static List<ToolResult> runBatch(ToolExecutor tools, List<ToolCallIntent> calls) {
        if (calls.isEmpty()) {
            return List.of();
        }
        if (calls.size() == 1) {
            return List.of(executeOne(tools, calls.getFirst()));
        }
        List<CompletableFuture<ToolResult>> futures = new ArrayList<>(calls.size());
        for (ToolCallIntent call : calls) {
            futures.add(CompletableFuture.supplyAsync(() -> executeOne(tools, call), EXECUTOR));
        }
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private static ToolResult executeOne(ToolExecutor tools, ToolCallIntent call) {
        try {
            return tools.execute(call.id(), call.name(), call.arguments());
        } catch (IllegalArgumentException e) {
            return ToolExecutor.invalidArgs(call.id(), call.name(), e.getMessage());
        }
    }
}
