package com.anvil.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CoreModuleTest {

    @Test
    void moduleLoads() {
        assertNotNull(CoreModule.class);
    }
}
