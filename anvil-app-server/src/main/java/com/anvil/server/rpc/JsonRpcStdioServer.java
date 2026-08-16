package com.anvil.server.rpc;

import com.anvil.protocol.ProtocolJson;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** JSON-RPC 2.0 在 stdin/stdout 上的换行分隔 JSON 传输层（Codex App Server 风格）。 */
@Component
public class JsonRpcStdioServer {

    private final JsonRpcDispatcher dispatcher;

    /**
     * 构造函数：注入 JSON-RPC 分发器。
     *
     * @param dispatcher JSON-RPC 分发器
     */
    public JsonRpcStdioServer(JsonRpcDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * 启动 stdin/stdout 事件循环：逐行读取请求并写出响应。
     *
     * @param input  标准输入流
     * @param output 标准输出流
     */
    public void run(InputStream input, OutputStream output) {
        PrintWriter writer =
                new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true);
        Object writeLock = new Object();

        try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                handleLine(line, writer, writeLock);
            }
        } catch (Exception e) {
            synchronized (writeLock) {
                writer.println(ProtocolJson.toJson(
                        JsonRpcResponse.error(null, JsonRpcDispatcher.INTERNAL_ERROR, e.getMessage())));
            }
        }
    }

    /**
     * 处理单行 JSON-RPC 请求：解析、分发并写出响应。
     *
     * @param line      一行请求文本
     * @param writer    输出写入器
     * @param writeLock 输出同步锁（保证并发写不交错）
     */
    private void handleLine(String line, PrintWriter writer, Object writeLock) {
        JsonRpcRequest request;
        try {
            JsonNode node = ProtocolJson.mapper().readTree(line);
            request = new JsonRpcRequest(
                    node.path("jsonrpc").asText(null),
                    node.has("id") && !node.get("id").isNull() ? node.get("id") : null,
                    node.path("method").asText(null),
                    node.get("params"));
        } catch (Exception e) {
            synchronized (writeLock) {
                writer.println(ProtocolJson.toJson(
                        JsonRpcResponse.error(null, JsonRpcDispatcher.PARSE_ERROR, "parse error")));
            }
            return;
        }

        JsonRpcResponse response = dispatcher.dispatch(
                request,
                note -> {
                    synchronized (writeLock) {
                        Map<String, Object> notification = Map.of(
                                "jsonrpc", "2.0",
                                "method", "event/notification",
                                "params", note);
                        writer.println(ProtocolJson.toJson(notification));
                    }
                });

        if (request.id() != null) {
            synchronized (writeLock) {
                writer.println(ProtocolJson.toJson(response));
            }
        }
    }
}
