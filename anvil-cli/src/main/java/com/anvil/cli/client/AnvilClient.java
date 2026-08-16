package com.anvil.cli.client;

import com.anvil.protocol.ProtocolJson;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Anvil 服务器 REST + SSE 客户端：为 CLI 提供精简的 HTTP 调用封装。
 *
 * <p>覆盖四类能力：健康检查、线程/运行的创建与启动、审批响应，
 * 以及通过 SSE 长连接订阅运行事件流（{@link #attachRun}）。
 * 所有请求体与响应体均通过 {@link ProtocolJson} 序列化/反序列化。</p>
 */
public final class AnvilClient {

    /** 底层 HTTP 客户端（10 秒连接超时）。 */
    private final HttpClient http;
    /** 服务器基础地址（已去除末尾斜杠）。 */
    private final String baseUrl;

    /**
     * 构造客户端。
     *
     * @param baseUrl 服务器基础地址，如 {@code http://127.0.0.1:7788}
     */
    public AnvilClient(String baseUrl) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /**
     * 健康检查。
     *
     * @return 健康接口返回的 JSON 映射
     */
    public Map<String, Object> health() throws Exception {
        return getJson("/api/health");
    }

    /**
     * 创建一个新线程。
     *
     * @param cwd 线程绑定的工作区根目录
     * @return 创建响应 JSON 映射（含线程 id）
     */
    public Map<String, Object> createThread(String cwd) throws Exception {
        return postJson("/v1/threads", Map.of("cwd", cwd));
    }

    /**
     * 在指定线程下启动一次运行。
     *
     * @param threadId 线程标识
     * @param mode     运行模式（交互/自动/审批等）
     * @param model    模型标识
     * @param message  发送给模型的首条用户消息
     * @return 启动响应 JSON 映射（含运行 id）
     */
    public Map<String, Object> startRun(String threadId, String mode, String model, String message) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", mode);
        body.put("model", model);
        body.put("message", message);
        return postJson("/v1/threads/" + threadId + "/runs", body);
    }

    /**
     * 对指定的审批请求做出响应。
     *
     * @param approvalId 审批请求标识
     * @param decision   决定（同意/拒绝等）
     */
    public void respondApproval(String approvalId, String decision) throws Exception {
        postJson("/v1/approvals/" + approvalId + "/respond", Map.of("decision", decision));
    }

    /**
     * 通过 SSE 长连接附加到运行事件流，并实时回调每个事件。
     *
     * <p>从服务器推送的 {@code data:} 行中逐条解析 JSON，
     * 交给 {@code onEvent} 消费；连接断开或出错时抛出异常。</p>
     *
     * @param runId   运行实例标识
     * @param fromSeq 起始事件序号（增量拉取起点）
     * @param onEvent 事件回调（每收到一个事件调用一次）
     */
    public void attachRun(String runId, int fromSeq, Consumer<JsonNode> onEvent) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/runs/" + runId + "/events?from_seq=" + fromSeq))
                .GET()
                .build();

        HttpResponse<java.io.InputStream> response =
                http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("attach failed: HTTP " + response.statusCode());
        }

        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if (!data.isEmpty()) {
                        onEvent.accept(ProtocolJson.mapper().readTree(data));
                    }
                }
            }
        }
    }

    /**
     * 发送 GET 请求并解析返回的 JSON 为 Map。
     *
     * @param path 相对路径（含开头斜杠）
     * @return 响应 JSON 映射
     */
    private Map<String, Object> getJson(String path) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        ensureOk(response);
        return ProtocolJson.mapFromJson(response.body());
    }

    /**
     * 发送 POST（JSON body）请求并解析返回的 JSON 为 Map。
     *
     * @param path 相对路径（含开头斜杠）
     * @param body 请求体；响应为空时返回空 Map
     * @return 响应 JSON 映射
     */
    private Map<String, Object> postJson(String path, Map<String, Object> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(ProtocolJson.toJson(body)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        ensureOk(response);
        if (response.body() == null || response.body().isBlank()) {
            return Map.of();
        }
        return ProtocolJson.mapFromJson(response.body());
    }

    /**
     * 校验响应状态码，>=400 时抛出异常并携带响应体信息。
     *
     * @param response HTTP 响应
     */
    private static void ensureOk(HttpResponse<String> response) {
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    /**
     * 去除基础地址末尾的斜杠，避免拼接路径时出现双斜杠。
     *
     * @param url 原始地址
     * @return 去除末尾斜杠后的地址
     */
    private static String trimTrailingSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
