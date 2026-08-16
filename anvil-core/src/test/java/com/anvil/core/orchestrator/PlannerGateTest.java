package com.anvil.core.orchestrator;

import com.anvil.core.loop.RunProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerGateTest {

    @Test
    void blocksWritesUntilPlanInComplexMode() {
        assertTrue(PlannerGate.blocksWrite(RunProfile.COMPLEX, true, false, "search_replace"));
        assertFalse(PlannerGate.blocksWrite(RunProfile.COMPLEX, true, false, "plan.update"));
        assertFalse(PlannerGate.blocksWrite(RunProfile.COMPLEX, true, true, "fs.write"));
    }
}
