package com.anvil.core.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpBridgeTest {

    @Test
    void allowlistBlocksUnknownServer() {
        McpBridge bridge = new McpBridge(
                List.of(new McpServerConfig("docs", "echo", List.of(), true)),
                Set.of("other"),
                1000L);
        assertFalse(bridge.isAllowedServer("docs"));
        assertTrue(bridge.isAllowedServer("other"));
    }

    @Test
    void builtinMcpToolsWhenAllowlisted() {
        McpBridge bridge = new McpBridge(List.of(), Set.of("junit"), 1000L);
        var schemas = bridge.toolSchemas();
        assertTrue(schemas.stream().anyMatch(s -> String.valueOf(s.get("function")).contains("junit") || nameOf(s).contains("junit")));
        var result = bridge.execute("tc1", "mcp.junit.report", Map.of("path", "target/surefire-reports"));
        assertEquals("ok", result.status());
    }

    @SuppressWarnings("unchecked")
    private static String nameOf(Map<String, Object> schema) {
        Object fn = schema.get("function");
        if (fn instanceof Map<?, ?> map) {
            return String.valueOf(map.get("name"));
        }
        return "";
    }

    @Test
    void detectsMcpToolNames() {
        McpBridge bridge = new McpBridge(List.of(), Set.of(), 1000L);
        assertTrue(bridge.isMcpTool("mcp.docs.search"));
        assertFalse(bridge.isMcpTool("fs.read"));
    }
}
