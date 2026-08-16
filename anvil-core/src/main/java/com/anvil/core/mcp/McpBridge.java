package com.anvil.core.mcp;

import com.anvil.protocol.ErrorCodes;
import com.anvil.protocol.ErrorInfo;
import com.anvil.protocol.ProtocolJson;
import com.anvil.protocol.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP stdio bridge with server allowlist. Tool names: {@code mcp.<server>.<tool>}.
 */
public final class McpBridge implements AutoCloseable {

    private final Map<String, McpServerConfig> configs;
    private final Set<String> allowlist;
    private final Map<String, McpSession> sessions = new ConcurrentHashMap<>();
    private final long rpcTimeoutMs;

    public McpBridge(List<McpServerConfig> servers, Set<String> allowlist, long rpcTimeoutMs) {
        this.configs = new LinkedHashMap<>();
        if (servers != null) {
            for (McpServerConfig s : servers) {
                if (s.enabled()) {
                    configs.put(s.name(), s);
                }
            }
        }
        this.allowlist = allowlist == null ? Set.of() : Set.copyOf(allowlist);
        this.rpcTimeoutMs = rpcTimeoutMs <= 0 ? 30_000L : rpcTimeoutMs;
    }

    public boolean isMcpTool(String toolName) {
        return toolName != null && toolName.startsWith("mcp.");
    }

    public boolean isAllowedServer(String serverName) {
        return allowlist.contains(serverName);
    }

    public List<Map<String, Object>> toolSchemas() {
        List<Map<String, Object>> schemas = new ArrayList<>();
        for (String server : configs.keySet()) {
            if (!isAllowedServer(server)) {
                continue;
            }
            try {
                McpSession session = session(server);
                for (JsonNode tool : session.listTools()) {
                    String toolName = tool.path("name").asText();
                    schemas.add(toOpenAiSchema(server, toolName, tool.path("description").asText(""), tool.path("inputSchema")));
                }
            } catch (Exception ignored) {
                // skip unavailable MCP server in schema listing
            }
        }
        return schemas;
    }

    public ToolResult execute(String toolCallId, String toolName, Map<String, Object> arguments) {
        McpToolRef ref = parseToolName(toolName);
        if (!configs.containsKey(ref.server())) {
            return denied(toolCallId, toolName, "unknown MCP server: " + ref.server());
        }
        if (!isAllowedServer(ref.server())) {
            return denied(toolCallId, toolName, "MCP server not in allowlist: " + ref.server());
        }
        try {
            McpSession session = session(ref.server());
            JsonNode result = session.callTool(ref.tool(), arguments);
            String text = extractText(result);
            return ToolResult.ok(toolCallId, toolName, text);
        } catch (Exception e) {
            return new ToolResult(
                    toolCallId,
                    toolName,
                    "error",
                    "",
                    false,
                    null,
                    ErrorInfo.of(ErrorCodes.TOOL_FAILED, e.getMessage(), true));
        }
    }

    private McpSession session(String server) throws Exception {
        return sessions.computeIfAbsent(server, s -> {
            try {
                return McpSession.start(configs.get(s), rpcTimeoutMs);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static McpToolRef parseToolName(String toolName) {
        String[] parts = toolName.split("\\.", 3);
        if (parts.length != 3 || !"mcp".equals(parts[0])) {
            throw new IllegalArgumentException("invalid MCP tool name: " + toolName);
        }
        return new McpToolRef(parts[1], parts[2]);
    }

    private static Map<String, Object> toOpenAiSchema(String server, String tool, String description, JsonNode inputSchema) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", "mcp." + server + "." + tool);
        function.put("description", description.isBlank() ? ("MCP tool " + tool + " on " + server) : description);
        if (inputSchema != null && inputSchema.isObject()) {
            function.put("parameters", ProtocolJson.mapper().convertValue(inputSchema, Map.class));
        } else {
            function.put("parameters", Map.of("type", "object", "properties", Map.of()));
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "function");
        schema.put("function", function);
        return schema;
    }

    private static String extractText(JsonNode result) {
        JsonNode content = result.path("content");
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : content) {
                if ("text".equals(item.path("type").asText())) {
                    if (!sb.isEmpty()) {
                        sb.append('\n');
                    }
                    sb.append(item.path("text").asText());
                }
            }
            return sb.toString();
        }
        return result.toString();
    }

    private static ToolResult denied(String toolCallId, String name, String message) {
        return ToolResult.denied(toolCallId, name, ErrorInfo.of(ErrorCodes.POLICY_DENIED, message, false));
    }

    @Override
    public void close() {
        sessions.values().forEach(McpSession::close);
        sessions.clear();
    }

    private record McpToolRef(String server, String tool) {}

    private static final class McpSession {
        private final Process process;
        private final PrintWriter writer;
        private final BufferedReader reader;
        private final AtomicInteger idSeq = new AtomicInteger(1);
        private final long rpcTimeoutMs;
        private List<JsonNode> cachedTools;

        private McpSession(Process process, PrintWriter writer, BufferedReader reader, long rpcTimeoutMs) {
            this.process = process;
            this.writer = writer;
            this.reader = reader;
            this.rpcTimeoutMs = rpcTimeoutMs;
        }

        static McpSession start(McpServerConfig config, long rpcTimeoutMs) throws Exception {
            List<String> command = new ArrayList<>();
            command.add(config.command());
            command.addAll(config.args());
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            PrintWriter writer =
                    new PrintWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            McpSession session = new McpSession(process, writer, reader, rpcTimeoutMs);
            session.initialize();
            return session;
        }

        private void initialize() throws Exception {
            Map<String, Object> params = Map.of(
                    "protocolVersion", "2024-11-05",
                    "capabilities", Map.of(),
                    "clientInfo", Map.of("name", "anvil", "version", "0.1.0"));
            rpc("initialize", params);
            rpc("notifications/initialized", Map.of());
        }

        List<JsonNode> listTools() throws Exception {
            if (cachedTools != null) {
                return cachedTools;
            }
            JsonNode result = rpc("tools/list", Map.of());
            cachedTools = new ArrayList<>();
            result.path("tools").forEach(cachedTools::add);
            return cachedTools;
        }

        JsonNode callTool(String name, Map<String, Object> arguments) throws Exception {
            return rpc("tools/call", Map.of("name", name, "arguments", arguments == null ? Map.of() : arguments));
        }

        private JsonNode rpc(String method, Map<String, Object> params) throws Exception {
            int id = idSeq.getAndIncrement();
            Map<String, Object> request = Map.of(
                    "jsonrpc", "2.0",
                    "id", id,
                    "method", method,
                    "params", params);
            writer.println(ProtocolJson.toJson(request));

            long deadline = System.currentTimeMillis() + rpcTimeoutMs;
            while (System.currentTimeMillis() < deadline) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                JsonNode node = ProtocolJson.mapper().readTree(line);
                if (node.has("id") && node.get("id").asInt() == id) {
                    if (node.has("error")) {
                        throw new IllegalStateException(node.path("error").path("message").asText("MCP error"));
                    }
                    return node.path("result");
                }
            }
            throw new IllegalStateException("MCP RPC timeout: " + method);
        }

        void close() {
            try {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
