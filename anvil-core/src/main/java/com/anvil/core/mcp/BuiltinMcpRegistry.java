package com.anvil.core.mcp;

import com.anvil.protocol.ToolResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Built-in MCP tool stubs for junit / github / checkstyle (Phase 10.4). */
public final class BuiltinMcpRegistry {

    private static final Set<String> SERVERS = Set.of("junit", "github", "checkstyle");

    private BuiltinMcpRegistry() {}

    public static boolean isBuiltinServer(String server) {
        return server != null && SERVERS.contains(server);
    }

    public static List<Map<String, Object>> toolSchemas(Set<String> allowlist) {
        List<Map<String, Object>> schemas = new ArrayList<>();
        if (allowlist == null) {
            return schemas;
        }
        if (allowlist.contains("junit")) {
            schemas.add(schema("junit", "report", "Read latest JUnit/XML test report summary", Map.of(
                    "type", "object",
                    "properties", Map.of("path", Map.of("type", "string", "description", "Report path glob")))));
        }
        if (allowlist.contains("github")) {
            schemas.add(schema("github", "create_pr", "Open a GitHub pull request draft summary", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "title", Map.of("type", "string"),
                            "body", Map.of("type", "string")))));
        }
        if (allowlist.contains("checkstyle")) {
            schemas.add(schema("checkstyle", "scan", "Run checkstyle-style scan summary on Java paths", Map.of(
                    "type", "object",
                    "properties", Map.of("path", Map.of("type", "string", "description", "File or directory")))));
        }
        return schemas;
    }

    public static ToolResult execute(String toolCallId, String toolName, Map<String, Object> arguments) {
        McpToolRef ref = parse(toolName);
        String body =
                switch (ref.server()) {
                    case "junit" -> junitReport(arguments);
                    case "github" -> githubPr(arguments);
                    case "checkstyle" -> checkstyleScan(arguments);
                    default -> "unknown builtin MCP server: " + ref.server();
                };
        return ToolResult.ok(toolCallId, toolName, body);
    }

    private static String junitReport(Map<String, Object> args) {
        String path = args == null ? "" : String.valueOf(args.getOrDefault("path", "target/surefire-reports"));
        return """
                JUnit report (builtin MCP stub)
                path: %s
                tests: 0
                failures: 0
                skipped: 0
                hint: wire real MCP server via anvil.mcp.servers for live CI reports.
                """
                .formatted(path)
                .trim();
    }

    private static String githubPr(Map<String, Object> args) {
        String title = args == null ? "" : String.valueOf(args.getOrDefault("title", "Anvil changes"));
        return """
                GitHub PR draft (builtin MCP stub)
                title: %s
                status: not pushed — use git push + gh pr create in production.
                """
                .formatted(title)
                .trim();
    }

    private static String checkstyleScan(Map<String, Object> args) {
        String path = args == null ? "" : String.valueOf(args.getOrDefault("path", "src/main/java"));
        return """
                Checkstyle scan (builtin MCP stub)
                path: %s
                violations: 0
                hint: enable external checkstyle MCP via anvil.mcp.servers.
                """
                .formatted(path)
                .trim();
    }

    private static Map<String, Object> schema(String server, String tool, String description, Map<String, Object> parameters) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", "mcp." + server + "." + tool);
        function.put("description", description);
        function.put("parameters", parameters);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "function");
        schema.put("function", function);
        return schema;
    }

    private static McpToolRef parse(String toolName) {
        String[] parts = toolName.split("\\.", 3);
        return new McpToolRef(parts[1], parts[2]);
    }

    private record McpToolRef(String server, String tool) {}
}
