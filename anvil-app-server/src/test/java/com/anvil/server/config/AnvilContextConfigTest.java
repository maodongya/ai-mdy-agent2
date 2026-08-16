package com.anvil.server.config;

import com.anvil.core.compact.ContextBudget;
import com.anvil.core.loop.RunProfile;
import com.anvil.protocol.Mode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnvilContextConfigTest {

    private static AnvilContextConfig config(
            boolean autoAfterWrite, boolean autoCompile, String template, long timeoutMs) {
        return new AnvilContextConfig(
                120_000,
                80_000,
                12,
                8_000,
                40,
                autoAfterWrite,
                autoCompile,
                template,
                timeoutMs,
                true,
                true,
                true,
                true,
                true,
                true,
                6,
                8_000,
                500_000L,
                true,
                "deepseek:deepseek-chat",
                "deepseek:deepseek-chat",
                "deepseek:deepseek-reasoner");
    }

    @Test
    void budgetForProfileUsesHigherLimits() {
        AnvilContextConfig cfg = config(true, true, "", 180_000L);
        ContextBudget budget = cfg.budgetForProfile(RunProfile.STANDARD);

        assertEquals(120_000, budget.compactThresholdTokens());
        assertEquals(80_000, budget.targetTokensAfterCompact());
        assertEquals(12, budget.keepRecentMessages());
        assertEquals(8_000, budget.maxToolContentChars());
    }

    @Test
    void complexProfileWinsWhenHigherThanConfig() {
        AnvilContextConfig cfg = config(false, false, "mvn test -pl {module}", 60_000L);
        ContextBudget budget = cfg.budgetForProfile(RunProfile.COMPLEX);

        assertTrue(budget.compactThresholdTokens() >= RunProfile.COMPLEX.contextBudget().compactThresholdTokens());
        assertTrue(budget.keepRecentMessages() >= RunProfile.COMPLEX.contextBudget().keepRecentMessages());
    }

    @Test
    void verifyConfigFromProperties() {
        AnvilContextConfig cfg = config(true, true, "mvn -q test", 90_000L);

        assertTrue(cfg.verifyConfig().autoAfterWrite());
        assertTrue(cfg.verifyConfig().autoCompileAfterWrite());
        assertEquals("mvn -q test", cfg.verifyConfig().commandTemplate());
        assertEquals(90_000L, cfg.verifyConfig().timeoutMs());
        assertTrue(cfg.verifyConfig().injectFailuresIntoHistory());
        assertTrue(cfg.loopConfig().parallelReadTools());
        assertTrue(cfg.loopConfigForProfile(RunProfile.COMPLEX).plannerRequired());
    }

    @Test
    void verifyForExtendedAgentEnablesAutoVerify() {
        AnvilContextConfig cfg = config(false, true, "mvn test", 90_000L);
        assertTrue(cfg.verifyFor(Mode.AGENT, RunProfile.EXTENDED).autoAfterWrite());
    }

    @Test
    void verifyForStandardAgentStaysOffWhenYamlFalse() {
        AnvilContextConfig cfg = config(false, true, "mvn test", 90_000L);
        assertFalse(cfg.verifyFor(Mode.AGENT, RunProfile.STANDARD).autoAfterWrite());
    }
}
