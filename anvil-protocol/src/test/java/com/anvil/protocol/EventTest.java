package com.anvil.protocol;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventTest {

    @Test
    void rejectsNegativeSeq() {
        assertThrows(IllegalArgumentException.class, () -> new Event(
                ProtocolConstants.PROTOCOL_VERSION,
                "thr_1",
                "run_1",
                -1,
                "run.started",
                "2026-08-13T00:00:00Z",
                Map.of()));
    }

    @Test
    void acceptsMinimalRunStarted() {
        Event event = new Event(
                ProtocolConstants.PROTOCOL_VERSION,
                "thr_1",
                "run_1",
                0,
                "run.started",
                "2026-08-13T00:00:00Z",
                Map.of("mode", "ask", "model", "scripted:read-add"));

        assertEquals(0, event.seq());
        assertEquals("run.started", event.type());
        assertEquals(ProtocolConstants.PROTOCOL_VERSION, event.protocolVersion());
    }
}
