package com.anvil.server.config;

import com.anvil.core.compact.ContextBudget;
import com.anvil.core.loop.RunProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnvilContextConfigTest {

    @Test
    void budgetForProfileUsesHigherLimits() {
        AnvilContextConfig config = new AnvilContextConfig(200_000, 130_000, 32, 20_000, 50, true, "", 180_000L, true, true);
        ContextBudget budget = config.budgetForProfile(RunProfile.STANDARD);

        assertEquals(200_000, budget.compactThresholdTokens());
        assertEquals(130_000, budget.targetTokensAfterCompact());
        assertEquals(32, budget.keepRecentMessages());
        assertEquals(20_000, budget.maxToolContentChars());
    }

    @Test
    void complexProfileWinsWhenHigherThanConfig() {
        AnvilContextConfig config = new AnvilContextConfig(120_000, 80_000, 12, 8_000, 40, false, "mvn test -pl {module}", 60_000L, false, false);
        ContextBudget budget = config.budgetForProfile(RunProfile.COMPLEX);

        assertTrue(budget.compactThresholdTokens() >= RunProfile.COMPLEX.contextBudget().compactThresholdTokens());
        assertTrue(budget.keepRecentMessages() >= RunProfile.COMPLEX.contextBudget().keepRecentMessages());
    }

    @Test
    void verifyConfigFromProperties() {
        AnvilContextConfig config = new AnvilContextConfig(120_000, 80_000, 12, 8_000, 40, true, "mvn -q test", 90_000L, true, true);

        assertTrue(config.verifyConfig().autoAfterWrite());
        assertEquals("mvn -q test", config.verifyConfig().commandTemplate());
        assertEquals(90_000L, config.verifyConfig().timeoutMs());
        assertTrue(config.verifyConfig().injectFailuresIntoHistory());
        assertTrue(config.loopConfig().parallelReadTools());
    }
}
