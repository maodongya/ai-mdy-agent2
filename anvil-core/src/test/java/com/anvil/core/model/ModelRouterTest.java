package com.anvil.core.model;

import com.anvil.core.loop.RunProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelRouterTest {

    @Test
    void complexProfileStartsWithPlan() {
        assertEquals(
                ModelRouter.StepKind.PLAN,
                ModelRouter.classify(List.of(), RunProfile.COMPLEX, 1));
    }

    @Test
    void routesExploreEditPlan() {
        ModelRoutingConfig cfg = ModelRoutingConfig.deepSeekDefaults(true);
        assertEquals("deepseek:deepseek-chat", ModelRouter.route("deepseek:deepseek-chat", ModelRouter.StepKind.EXPLORE, cfg));
        assertEquals("deepseek:deepseek-reasoner", ModelRouter.route("deepseek:deepseek-chat", ModelRouter.StepKind.PLAN, cfg));
    }

    @Test
    void editAfterWriteTool() {
        List<Map<String, Object>> history = List.of(
                Map.of(
                        "role",
                        "assistant",
                        "tool_calls",
                        List.of(Map.of("function", Map.of("name", "search_replace")))));
        assertEquals(ModelRouter.StepKind.EDIT, ModelRouter.classify(history, RunProfile.STANDARD, 5));
    }
}
