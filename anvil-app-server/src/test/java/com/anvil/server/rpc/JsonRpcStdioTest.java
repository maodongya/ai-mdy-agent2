package com.anvil.server.rpc;

import com.anvil.protocol.ProtocolJson;
import com.anvil.server.service.RunService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JsonRpcStdioTest {

    @Autowired
    JsonRpcStdioServer stdioServer;

    @Test
    void threadCreateAndRunStartOverStdio() throws Exception {
        Path workspace = repoRoot().resolve("fixtures/repos/sample-lib");
        String cwd = workspace.toString().replace("\\", "\\\\");

        String createLine =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"thread/create\",\"params\":{\"cwd\":\"" + cwd + "\"}}";

        ByteArrayOutputStream step1Out = new ByteArrayOutputStream();
        stdioServer.run(new ByteArrayInputStream(createLine.getBytes(StandardCharsets.UTF_8)), step1Out);
        JsonNode createResp = ProtocolJson.mapper().readTree(step1Out.toString(StandardCharsets.UTF_8).trim());
        String threadId = createResp.path("result").path("thread_id").asText();

        String runLine = """
                {"jsonrpc":"2.0","id":2,"method":"run/start","params":{"thread_id":"%s","mode":"agent","model":"scripted:read-add","message":"read"}}
                """
                .formatted(threadId);

        ByteArrayOutputStream step2Out = new ByteArrayOutputStream();
        stdioServer.run(new ByteArrayInputStream(runLine.getBytes(StandardCharsets.UTF_8)), step2Out);

        String runResponse = step2Out.toString(StandardCharsets.UTF_8);
        assertTrue(runResponse.contains("\"run_id\""));
    }

    private static Path repoRoot() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        if (Files.isDirectory(cwd.resolve("fixtures"))) {
            return cwd;
        }
        return cwd.getParent();
    }
}
