package com.anvil.tools.lsp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Minimal LSP JSON-RPC client over stdio Content-Length framing. */
public final class LspJsonRpc implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Process process;
    private final OutputStream out;
    private final BufferedReader in;
    private final AtomicInteger idSeq = new AtomicInteger(1);
    private volatile boolean closed;

    public LspJsonRpc(Process process) {
        this.process = process;
        this.out = process.getOutputStream();
        this.in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    }

    public int nextId() {
        return idSeq.getAndIncrement();
    }

    public synchronized void notify(String method, Object params) throws IOException {
        ObjectNode root = JSON.createObjectNode();
        root.put("jsonrpc", "2.0");
        root.put("method", method);
        root.set("params", JSON.valueToTree(params));
        writeMessage(root);
    }

    public synchronized JsonNode request(int id, String method, Object params, long timeoutMs) throws IOException {
        ObjectNode root = JSON.createObjectNode();
        root.put("jsonrpc", "2.0");
        root.put("id", id);
        root.put("method", method);
        root.set("params", JSON.valueToTree(params));
        writeMessage(root);
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            JsonNode msg = readMessage(Math.min(500, deadline - System.currentTimeMillis()));
            if (msg == null) {
                continue;
            }
            if (msg.has("id") && msg.get("id").asInt() == id) {
                if (msg.has("error")) {
                    throw new IOException("LSP error: " + msg.get("error"));
                }
                return msg.get("result");
            }
        }
        throw new IOException("LSP request timeout: " + method);
    }

    public static List<LspLocation> parseLocations(JsonNode result, Path workspaceRoot) {
        if (result == null || result.isNull()) {
            return List.of();
        }
        List<LspLocation> out = new ArrayList<>();
        if (result.isArray()) {
            for (JsonNode item : result) {
                parseLocation(item, workspaceRoot).ifPresent(out::add);
            }
        } else {
            parseLocation(result, workspaceRoot).ifPresent(out::add);
        }
        return out;
    }

    private static Optional<LspLocation> parseLocation(JsonNode node, java.nio.file.Path workspaceRoot) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        JsonNode target = node.has("uri") ? node : node.get("target");
        if (target == null || !target.has("uri")) {
            return Optional.empty();
        }
        String uri = target.get("uri").asText("");
        JsonNode range = target.has("range") ? target.get("range") : node.get("range");
        int line = 1;
        int col = 1;
        if (range != null && range.has("start")) {
            line = range.get("start").path("line").asInt(0) + 1;
            col = range.get("start").path("character").asInt(0) + 1;
        }
        String path = uriToRelative(uri, workspaceRoot);
        if (path.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new LspLocation(path, line, col, "jdtls"));
    }

    static String uriToRelative(String uri, java.nio.file.Path workspaceRoot) {
        if (uri == null || uri.isBlank()) {
            return "";
        }
        try {
            if (uri.startsWith("file:")) {
                java.nio.file.Path abs = java.nio.file.Path.of(java.net.URI.create(uri)).normalize();
                java.nio.file.Path rel = workspaceRoot.toAbsolutePath().normalize().relativize(abs);
                if (rel.startsWith("..")) {
                    return "";
                }
                return rel.toString().replace('\\', '/');
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "";
    }

    private void writeMessage(ObjectNode root) throws IOException {
        byte[] body = JSON.writeValueAsBytes(root);
        String header = "Content-Length: " + body.length + "\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private JsonNode readMessage(long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String line = in.readLine();
            if (line == null) {
                return null;
            }
            if (!line.startsWith("Content-Length:")) {
                continue;
            }
            int length = Integer.parseInt(line.substring("Content-Length:".length()).trim());
            in.readLine(); // empty
            char[] buf = new char[length];
            int read = 0;
            while (read < length) {
                int n = in.read(buf, read, length - read);
                if (n < 0) {
                    return null;
                }
                read += n;
            }
            return JSON.readTree(new String(buf));
        }
        return null;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            out.close();
        } catch (IOException ignored) {
        }
        process.destroyForcibly();
    }

    public static ObjectNode textDocumentItem(String uri, String languageId, int version, String text) {
        ObjectNode node = JSON.createObjectNode();
        node.put("uri", uri);
        node.put("languageId", languageId);
        node.put("version", version);
        node.put("text", text);
        return node;
    }

    public static ObjectNode position(int line0, int character0) {
        ObjectNode node = JSON.createObjectNode();
        node.put("line", line0);
        node.put("character", character0);
        return node;
    }

    public static ArrayNode singleEdit(ObjectNode range, String text) {
        ObjectNode change = JSON.createObjectNode();
        change.set("range", range);
        change.put("newText", text);
        ArrayNode arr = JSON.createArrayNode();
        arr.add(change);
        return arr;
    }
}
