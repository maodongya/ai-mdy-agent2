package com.anvil.ui.client;

import com.anvil.protocol.Event;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnvilClientTest {

    @Test
    void parseSseReadsEventLines() throws Exception {
        String sse =
                """
                event: event
                data: {"protocol_version":"1.0","thread_id":"t1","run_id":"r1","seq":0,"type":"message.completed","ts":"2026-01-01T00:00:00Z","payload":{"text":"hi"}}

                event: event
                data: {"protocol_version":"1.0","thread_id":"t1","run_id":"r1","seq":1,"type":"run.completed","ts":"2026-01-01T00:00:01Z","payload":{}}

                """;
        List<Event> events = new ArrayList<>();
        AnvilClient.parseSse(new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)), events::add);
        assertEquals(2, events.size());
        assertEquals("message.completed", events.get(0).type());
        assertEquals("run.completed", events.get(1).type());
    }
}
