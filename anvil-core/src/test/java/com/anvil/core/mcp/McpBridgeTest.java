package com.anvil.core.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

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
    void detectsMcpToolNames() {
        McpBridge bridge = new McpBridge(List.of(), Set.of(), 1000L);
        assertTrue(bridge.isMcpTool("mcp.docs.search"));
        assertFalse(bridge.isMcpTool("fs.read"));
    }
}
