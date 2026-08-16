package com.anvil.core.orchestrator;

import com.anvil.core.loop.LoopConfig;
import com.anvil.core.loop.RunProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExploreGateTest {

    @Test
    void skipsWhenDisabled() {
        assertFalse(ExploreGate.shouldRun(RunProfile.COMPLEX, LoopConfig.disabledParallel(), "refactor across modules"));
    }

    @Test
    void runsForComplexProfileWhenEnabled() {
        LoopConfig config = new LoopConfig(true, true, true, false, 4);
        assertTrue(ExploreGate.shouldRun(RunProfile.COMPLEX, config, "fix typo"));
    }

    @Test
    void runsOnMultiFileKeywords() {
        LoopConfig config = new LoopConfig(true, true, true, false, 4);
        assertTrue(ExploreGate.shouldRun(RunProfile.STANDARD, config, "refactor across the codebase"));
    }
}
