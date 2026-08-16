package com.anvil.core.orchestrator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanParserTest {

    @Test
    void parsesCheckboxAndNumberedSteps() {
        String plan =
                """
                # Plan
                - [ ] Add Foo service
                - [x] Read existing code
                1. Wire controller
                2) Add tests
                """;
        List<String> steps = PlanParser.steps(plan);
        assertEquals(4, steps.size());
        assertTrue(steps.get(0).contains("Foo service"));
        assertTrue(steps.get(2).contains("Wire controller"));
    }

    @Test
    void formatsStepsBlock() {
        String block = PlanParser.formatStepsBlock(List.of("step A", "step B"));
        assertTrue(block.contains("<plan_steps"));
        assertTrue(block.contains("1. step A"));
    }
}
