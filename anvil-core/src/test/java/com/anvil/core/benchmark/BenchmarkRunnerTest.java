package com.anvil.core.benchmark;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkRunnerTest {

    @Test
    void subsequenceMatcherWorks() throws Exception {
        assertTrue(BenchmarkRunner.evaluate(
                        specWithContains(List.of("run.started", "tool.completed", "run.completed")),
                        events("run.started", "step.started", "tool.completed", "run.completed"),
                        null)
                .passed());
    }

    private static BenchmarkSpec specWithContains(List<String> contains) {
        return new BenchmarkSpec(
                "t",
                "t",
                "",
                "fixtures/repos/sample-lib",
                null,
                "fixtures/models/read-add.jsonl",
                "agent",
                "x",
                5,
                new BenchmarkSpec.BenchmarkExpect(
                        "succeeded", null, contains, null, null, null, null, null, null));
    }

    private static com.anvil.core.loop.LoopResult events(String... types) {
        var list = new java.util.ArrayList<com.anvil.protocol.Event>();
        int seq = 0;
        for (String type : types) {
            list.add(new com.anvil.protocol.Event("1.0", "t", "r", seq++, type, "2026-01-01T00:00:00Z", java.util.Map.of()));
        }
        return new com.anvil.core.loop.LoopResult(list, com.anvil.protocol.RunStatus.SUCCEEDED);
    }
}
