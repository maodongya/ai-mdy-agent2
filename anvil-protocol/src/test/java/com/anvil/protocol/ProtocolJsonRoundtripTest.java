package com.anvil.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolJsonRoundtripTest {

    private final ObjectMapper mapper = ProtocolJson.mapper();

    @Test
    void eventUsesSnakeCaseWireFormat() throws Exception {
        Event event = new Event(
                ProtocolConstants.PROTOCOL_VERSION,
                "thr_abc",
                "run_xyz",
                3,
                "tool.completed",
                "2026-08-13T12:00:00Z",
                Map.of("name", "fs.read", "status", "ok"));

        String json = mapper.writeValueAsString(event);
        JsonNode node = mapper.readTree(json);

        assertEquals("1.0", node.get("protocol_version").asText());
        assertEquals("thr_abc", node.get("thread_id").asText());
        assertEquals("run_xyz", node.get("run_id").asText());
        assertEquals(3, node.get("seq").asInt());
        assertEquals("tool.completed", node.get("type").asText());

        Event back = mapper.readValue(json, Event.class);
        assertEquals(event, back);
    }

    @Test
    void runSerializesModeAndStatusAsWireValues() throws Exception {
        Run run = new Run(
                "run_1",
                "thr_1",
                Mode.AGENT,
                "scripted:write-add",
                RunStatus.WAITING_APPROVAL,
                Usage.empty(),
                null);

        JsonNode node = mapper.readTree(mapper.writeValueAsString(run));
        assertEquals("agent", node.get("mode").asText());
        assertEquals("waiting_approval", node.get("status").asText());

        Run back = mapper.readValue(node.toString(), Run.class);
        assertEquals(Mode.AGENT, back.mode());
        assertEquals(RunStatus.WAITING_APPROVAL, back.status());
    }

    @Test
    void threadRoundtrip() throws Exception {
        Thread thread = new Thread(
                "thr_1",
                "demo",
                new WorkspaceRef("/tmp/ws", SandboxTier.WORKSPACE_WRITE, null),
                "2026-08-13T00:00:00Z",
                "2026-08-13T00:00:00Z",
                ThreadStatus.ACTIVE);

        Thread back = mapper.readValue(mapper.writeValueAsString(thread), Thread.class);
        assertEquals(thread, back);
    }

    @Test
    void toolResultDenied() {
        ToolResult result = ToolResult.denied(
                "call_1",
                "fs.write",
                ErrorInfo.of(ErrorCodes.POLICY_DENIED, "mode ask cannot write", false));
        assertEquals("denied", result.status());
        assertTrue(result.error().code().equals(ErrorCodes.POLICY_DENIED));
    }
}
