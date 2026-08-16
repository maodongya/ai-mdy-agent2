package com.anvil.core.benchmark;

import com.anvil.protocol.Mode;

import java.util.List;
import java.util.Map;

/** Benchmark task definition loaded from {@code fixtures/benchmarks/*.benchmark.json}. */
public record BenchmarkSpec(
        String id,
        String name,
        String description,
        String workspace,
        String workspaceFrom,
        String model,
        String mode,
        String userMessage,
        int maxSteps,
        BenchmarkExpect expect,
        boolean live) {

    public BenchmarkSpec {
        if (maxSteps <= 0) {
            maxSteps = 15;
        }
        if (mode == null || mode.isBlank()) {
            mode = "agent";
        }
        if (expect == null) {
            expect = BenchmarkExpect.empty();
        }
    }

    /** Backward-compatible ctor without live flag. */
    public BenchmarkSpec(
            String id,
            String name,
            String description,
            String workspace,
            String workspaceFrom,
            String model,
            String mode,
            String userMessage,
            int maxSteps,
            BenchmarkExpect expect) {
        this(id, name, description, workspace, workspaceFrom, model, mode, userMessage, maxSteps, expect, false);
    }

    public Mode modeEnum() {
        return Mode.fromWire(mode);
    }

    public boolean usesTempWorkspace() {
        return "temp".equalsIgnoreCase(workspace);
    }

    public record BenchmarkExpect(
            String status,
            List<String> eventTypes,
            List<String> eventTypesContains,
            List<String> forbidEventTypes,
            Integer maxToolCalls,
            Integer maxStepEvents,
            Map<String, String> fileContains,
            Map<String, String> fileEquals,
            List<String> fileExists) {

        public static BenchmarkExpect empty() {
            return new BenchmarkExpect(null, null, null, null, null, null, null, null, null);
        }
    }
}
