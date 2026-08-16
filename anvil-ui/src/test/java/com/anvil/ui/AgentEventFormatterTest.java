package com.anvil.ui;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEventFormatterTest {

    @Test
    void formatsStepAndModelEvents() {
        String step = AgentEventFormatter.format(
                "step.started",
                Map.of("step", 2, "context_messages", 8, "context_tokens_estimate", 4096, "tools_available", 5));
        assertTrue(step.contains("step 2"));
        assertTrue(step.contains("4096"));

        String model = AgentEventFormatter.format(
                "model.completed",
                Map.of(
                        "step", 2,
                        "kind", "tool_calls",
                        "input_tokens", 1000,
                        "output_tokens", 200,
                        "latency_ms", 850,
                        "usage_total",
                        Map.of("input_tokens", 3000, "output_tokens", 600, "tool_calls", 1)));
        assertTrue(model.contains("in=1000"));
        assertTrue(model.contains("850ms"));
        assertTrue(model.contains("Σ in=3000"));
    }

    @Test
    void formatsRunFailedWithErrorMessage() {
        String line = AgentEventFormatter.format(
                "run.failed",
                Map.of("error", Map.of("message", "SSE idle timeout (15m)")));
        assertTrue(line.contains("run failed · SSE idle timeout"));
    }

    @Test
    void formatsEditAndVerifyEvents() {
        String edit = AgentEventFormatter.format(
                "edit.summary", Map.of("path", "Foo.java", "lines_added", 3, "lines_removed", 1));
        assertTrue(edit.contains("Foo.java"));
        assertTrue(edit.contains("+3"));
        assertTrue(edit.contains("-1"));

        String verify = AgentEventFormatter.format(
                "verify.failed", Map.of("command", "mvn test", "preview", "BUILD FAILURE"));
        assertTrue(verify.contains("verify failed"));
        assertTrue(verify.contains("mvn test"));
    }
}
