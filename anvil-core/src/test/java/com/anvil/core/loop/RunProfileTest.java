package com.anvil.core.loop;

import com.anvil.protocol.Mode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunProfileTest {

    @Test
    void defaultsAgentToExtended() {
        assertEquals(RunProfile.EXTENDED, RunProfile.defaultFor(Mode.AGENT));
        assertEquals(RunProfile.STANDARD, RunProfile.defaultFor(Mode.ASK));
    }

    @Test
    void parsesWireValues() {
        assertEquals(RunProfile.COMPLEX, RunProfile.fromWire("complex"));
        assertEquals(RunProfile.EXTENDED, RunProfile.fromWire("long"));
        assertTrue(RunProfile.COMPLEX.defaultMaxSteps() >= RunProfile.EXTENDED.defaultMaxSteps());
    }
}
