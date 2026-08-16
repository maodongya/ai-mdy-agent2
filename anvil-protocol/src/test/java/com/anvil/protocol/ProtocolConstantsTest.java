package com.anvil.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtocolConstantsTest {

    @Test
    void protocolVersionIsOneDotZero() {
        assertEquals("1.0", ProtocolConstants.PROTOCOL_VERSION);
    }
}
