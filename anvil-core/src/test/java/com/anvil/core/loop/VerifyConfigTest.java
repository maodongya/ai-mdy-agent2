package com.anvil.core.loop;

import com.anvil.protocol.Mode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifyConfigTest {

    @Test
    void extendedAgentDefaultsToAutoVerify() {
        VerifyConfig base = new VerifyConfig(false, "", 90_000L, true, true, true);
        VerifyConfig resolved = VerifyConfig.forRun(base, Mode.AGENT, RunProfile.EXTENDED);
        assertTrue(resolved.autoAfterWrite());
        assertTrue(resolved.autoCompileAfterWrite());
    }

    @Test
    void standardAgentStaysOffUnlessYamlEnabled() {
        VerifyConfig base = new VerifyConfig(false, "", 90_000L, true, true, true);
        VerifyConfig resolved = VerifyConfig.forRun(base, Mode.AGENT, RunProfile.STANDARD);
        assertFalse(resolved.autoAfterWrite());
    }

    @Test
    void yamlEnableAppliesToAllProfiles() {
        VerifyConfig base = new VerifyConfig(true, "mvn test", 60_000L, true, false, true);
        VerifyConfig resolved = VerifyConfig.forRun(base, Mode.AGENT, RunProfile.STANDARD);
        assertTrue(resolved.autoAfterWrite());
    }
}
