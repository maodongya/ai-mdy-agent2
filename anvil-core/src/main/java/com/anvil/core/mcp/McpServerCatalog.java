package com.anvil.core.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Default MCP server definitions (Phase 10.4). */
public final class McpServerCatalog {

    private McpServerCatalog() {}

    public static List<McpServerConfig> defaultsFromYaml(List<Map<String, String>> entries) {
        if (entries == null || entries.isEmpty()) {
            return documentedDefaults();
        }
        List<McpServerConfig> configs = new ArrayList<>();
        for (Map<String, String> entry : entries) {
            String name = entry.get("name");
            if (name == null || name.isBlank()) {
                continue;
            }
            String command = entry.getOrDefault("command", "echo");
            String argsRaw = entry.getOrDefault("args", "");
            List<String> args = argsRaw.isBlank()
                    ? List.of()
                    : List.of(argsRaw.split("\\s+"));
            boolean enabled = Boolean.parseBoolean(entry.getOrDefault("enabled", "false"));
            configs.add(new McpServerConfig(name, command, args, enabled));
        }
        return configs;
    }

    /** Documented external MCP launchers (disabled until configured). */
    public static List<McpServerConfig> documentedDefaults() {
        return List.of(
                new McpServerConfig(
                        "junit",
                        "npx",
                        List.of("-y", "@modelcontextprotocol/server-everything"),
                        false),
                new McpServerConfig("github", "npx", List.of("-y", "@modelcontextprotocol/server-github"), false),
                new McpServerConfig(
                        "checkstyle",
                        "java",
                        List.of("-jar", "checkstyle-mcp.jar"),
                        false));
    }

    public static List<McpServerConfig> merge(List<McpServerConfig> base, List<McpServerConfig> overrides) {
        Map<String, McpServerConfig> merged = new LinkedHashMap<>();
        for (McpServerConfig c : base) {
            merged.put(c.name(), c);
        }
        for (McpServerConfig c : overrides) {
            merged.put(c.name(), c);
        }
        return List.copyOf(merged.values());
    }
}
