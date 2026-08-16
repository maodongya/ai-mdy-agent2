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
        BenchmarkExpect expect) {

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
