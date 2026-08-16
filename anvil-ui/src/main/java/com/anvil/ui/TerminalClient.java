package com.anvil.ui;

import com.anvil.protocol.ProtocolJson;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 终端（Terminal）HTTP+SSE 客户端，桥接 App Server 的 /v1/terminal 接口。
 */
public final class TerminalClient {

    public record SessionInfo(String sessionId, String cwd, String status) {}

    private final URI baseUrl;
    private final HttpClient http;

    public TerminalClient(String baseUrl) {
        this.baseUrl = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** 新建终端会话。 */
    public SessionInfo createSession(String threadId, String title) throws Exception {
        Map<String, Object> resp = postMap(
                "/v1/terminal/sessions",
                Map.of("thread_id", threadId, "title", title == null ? "bash" : title));
        return new SessionInfo(
                String.valueOf(resp.get("session_id")),
                String.valueOf(resp.getOrDefault("cwd", "")),
                String.valueOf(resp.getOrDefault("status", "READY")));
    }

    /** 执行命令。 */
    public void exec(String sessionId, String command) throws Exception {
        postMap("/v1/terminal/sessions/" + enc(sessionId) + "/exec", Map.of("command", command));
    }

    /** 终止当前命令。 */
    public void stop(String sessionId) {
        try {
            postMap("/v1/terminal/sessions/" + enc(sessionId) + "/stop", Map.of());
        } catch (Exception ignored) {
            // 终止失败可忽略
        }
    }

    /**
     * 订阅会话事件流（SSE）。事件 payload 结构：
     * {type, session_id, seq, payload}
     */
    public Thread stream(String sessionId, long fromSeq, Consumer<Map<String, Object>> onEvent) {
        Thread t = new Thread(() -> {
            try {
                String url = baseUrl + "/v1/terminal/sessions/" + enc(sessionId) + "/events?from_seq=" + fromSeq;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", "text/event-stream")
                        .timeout(Duration.ofMinutes(30))
                        .GET()
                        .build();
                HttpResponse<java.io.InputStream> response =
                        http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() >= 400) {
                    throw new IllegalStateException("HTTP " + response.statusCode());
                }
                parseSse(response.body(), onEvent);
            } catch (Exception ignored) {
                // 连接结束或断开
            }
        }, "anvil-term-" + sessionId);
        t.setDaemon(true);
        t.start();
        return t;
    }

    @SuppressWarnings("unchecked")
    private static void parseSse(java.io.InputStream input, Consumer<Map<String, Object>> onEvent) throws Exception {
        try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            while (!Thread.currentThread().isInterrupted()) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                line = line.trim();
                if (line.isEmpty() || line.startsWith(":") || !line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    continue;
                }
                Map<String, Object> message = ProtocolJson.mapFromJson(data);
                Object params = message.get("params");
                if (params instanceof Map<?, ?> m) {
                    onEvent.accept((Map<String, Object>) m);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postMap(String path, Map<String, ?> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(baseUrl.resolve(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(ProtocolJson.toJson(body)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
        }
        if (response.body() == null || response.body().isBlank()) {
            return Map.of();
        }
        return ProtocolJson.mapFromJson(response.body());
    }

    private static String enc(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
