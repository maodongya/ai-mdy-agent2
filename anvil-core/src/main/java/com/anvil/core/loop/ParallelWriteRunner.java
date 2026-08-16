package com.anvil.core.loop;

import com.anvil.core.model.ToolCallIntent;
import com.anvil.core.policy.Decision;
import com.anvil.core.tools.ToolExecutor;
import com.anvil.protocol.SideEffect;
import com.anvil.protocol.ToolResult;
import com.anvil.tools.MultiFilePatch;
import com.anvil.tools.ToolSideEffects;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/** Parallel workspace writes when paths are independent (Phase 10.3). */
final class ParallelWriteRunner {

    private static final Executor EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static final Set<String> WRITE_TOOLS = Set.of("fs.write", "search_replace", "apply_patch");
    private static final ConcurrentHashMap<String, ReentrantLock> FILE_LOCKS = new ConcurrentHashMap<>();

    private ParallelWriteRunner() {}

    static boolean canParallelize(String toolName, Decision decision, boolean enabled) {
        if (!enabled || decision.type() != Decision.Type.ALLOW) {
            return false;
        }
        if (ToolSideEffects.forTool(toolName) != SideEffect.WRITE_WORKSPACE) {
            return false;
        }
        return WRITE_TOOLS.contains(toolName);
    }

    static boolean batchParallelizable(List<ToolCallIntent> batch) {
        Set<String> paths = new HashSet<>();
        for (ToolCallIntent call : batch) {
            List<String> targets = writePaths(call);
            if (targets.isEmpty() || targets.size() > 1) {
                return false;
            }
            String path = targets.getFirst();
            if (!paths.add(path)) {
                return false;
            }
        }
        return batch.size() > 1;
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
        List<String> paths = writePaths(call);
        List<ReentrantLock> locks = paths.stream()
                .map(p -> FILE_LOCKS.computeIfAbsent(p, k -> new ReentrantLock()))
                .toList();
        locks.forEach(ReentrantLock::lock);
        try {
            return tools.execute(call.id(), call.name(), call.arguments());
        } catch (IllegalArgumentException e) {
            return ToolExecutor.invalidArgs(call.id(), call.name(), e.getMessage());
        } finally {
            for (int i = locks.size() - 1; i >= 0; i--) {
                locks.get(i).unlock();
            }
        }
    }

    static List<String> writePaths(ToolCallIntent call) {
        if (call.arguments() == null) {
            return List.of();
        }
        Map<String, Object> args = call.arguments();
        return switch (call.name()) {
            case "fs.write", "search_replace", "apply_patch" -> {
                Object path = args.get("path");
                if (path == null && "apply_patch".equals(call.name())) {
                    String patch = String.valueOf(args.getOrDefault("patch", ""));
                    if (MultiFilePatch.isMultiFile(patch)) {
                        yield List.of();
                    }
                }
                yield path == null ? List.of() : List.of(String.valueOf(path));
            }
            default -> List.of();
        };
    }
}
