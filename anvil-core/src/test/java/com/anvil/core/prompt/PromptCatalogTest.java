package com.anvil.core.prompt;

import com.anvil.protocol.Mode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptCatalogTest {

    @Test
    void includesAntiPatternsAndModeInstructions() {
        assertTrue(PromptCatalog.antiPatterns().contains("NEVER use shell.exec for grep"));
        assertTrue(PromptCatalog.modeInstructions(Mode.PLAN).contains("plan.update"));
        assertTrue(PromptCatalog.toolFewShots(Mode.AGENT).contains("search_replace"));
    }
}
