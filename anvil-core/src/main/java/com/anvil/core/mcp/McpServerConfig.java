package com.anvil.core.mcp;

import java.util.List;

public record McpServerConfig(String name, String command, List<String> args, boolean enabled) {

    public McpServerConfig {
        args = args == null ? List.of() : List.copyOf(args);
    }
}
