package com.anvil.server.rpc;

import com.anvil.protocol.ApprovalDecision;
import com.anvil.protocol.Event;
import com.anvil.protocol.Mode;
import com.anvil.protocol.ProtocolJson;
import com.anvil.protocol.RunStatus;
import com.anvil.server.service.ApprovalService;
import com.anvil.server.service.RunService;
import com.anvil.server.store.InMemoryStore;
import com.anvil.server.store.RunRecord;
import com.anvil.server.store.ThreadRecord;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * JSON-RPC 2.0 协议分发器：将客户端的 JSON-RPC 请求路由到对应的方法调用，并统一处理错误编码。
 *
 * <p>支持的 RPC 方法与 REST API 一一对应：
 * <ul>
 *   <li>{@code thread/create} — 创建会话线程</li>
 *   <li>{@code thread/get} — 查询线程详情</li>
 *   <li>{@code run/start} — 启动一次运行</li>
 *   <li>{@code run/cancel} — 取消运行</li>
 *   <li>{@code approval/respond} — 回复审批（Approve / Deny 等）</li>
 *   <li>{@code event/subscribe} — 长轮询订阅运行事件流</li>
 * </ul>
 */
@Component
public class JsonRpcDispatcher {

    /** JSON-RPC 标准错误码：解析错误。 */
    public static final int PARSE_ERROR = -32700;
    /** JSON-RPC 标准错误码：无效请求。 */
    public static final int INVALID_REQUEST = -32600;
    /** JSON-RPC 标准错误码：方法不存在。 */
    public static final int METHOD_NOT_FOUND = -32601;
    /** JSON-RPC 标准错误码：参数无效。 */
    public static final int INVALID_PARAMS = -32602;
    /** JSON-RPC 标准错误码：内部错误。 */
    public static final int INTERNAL_ERROR = -32603;
    /** 自定义错误码：资源（线程 / 运行 / 审批）不存在。 */
    public static final int NOT_FOUND = -32004;

    private final RunService runService;
    private final ApprovalService approvalService;
    private final InMemoryStore store;

    /**
     * 构造函数：注入运行服务、审批服务与内存存储。
     *
     * @param runService      运行服务
     * @param approvalService 审批服务
     * @param store           内存存储
     */
    public JsonRpcDispatcher(RunService runService, ApprovalService approvalService, InMemoryStore store) {
        this.runService = runService;
        this.approvalService = approvalService;
        this.store = store;
    }

    /**
     * 分发单个 JSON-RPC 请求到对应方法。
     *
     * @param request           JSON-RPC 请求
     * @param notificationSink  事件通知回调（用于 event/subscribe 推送）
     * @return 标准的 JSON-RPC 响应
     */
    public JsonRpcResponse dispatch(JsonRpcRequest request, Consumer<Map<String, Object>> notificationSink) {
        if (request == null || !"2.0".equals(request.jsonrpc()) || request.method() == null) {
            return JsonRpcResponse.error(request == null ? null : request.id(), INVALID_REQUEST, "invalid request");
        }

        try {
            Object result =
                    switch (request.method()) {
                        case "thread/create" -> threadCreate(request.params());
                        case "thread/get" -> threadGet(request.params());
                        case "run/start" -> runStart(request.params());
                        case "run/cancel" -> runCancel(request.params());
                        case "approval/respond" -> approvalRespond(request.params());
                        case "event/subscribe" -> eventSubscribe(request.params(), notificationSink);
                        default -> throw new RpcException(METHOD_NOT_FOUND, "method not found: " + request.method());
                    };
            return JsonRpcResponse.ok(request.id(), result);
        } catch (RpcException e) {
            return JsonRpcResponse.error(request.id(), e.code(), e.getMessage());
        } catch (Exception e) {
            return JsonRpcResponse.error(request.id(), INTERNAL_ERROR, e.getMessage());
        }
    }

    /** {@code thread/create}：创建会话线程。 */
    private Map<String, Object> threadCreate(JsonNode params) {
        String cwd = textOr(params, "cwd", ".");
        ThreadRecord thread = runService.createThread(Path.of(cwd).toAbsolutePath().normalize());
        return threadMap(thread);
    }

    /** {@code thread/get}：查询线程详情，不存在则抛出 NOT_FOUND。 */
    private Map<String, Object> threadGet(JsonNode params) {
        String threadId = required(params, "thread_id");
        ThreadRecord thread = runService
                .getThread(threadId)
                .orElseThrow(() -> new RpcException(NOT_FOUND, "thread not found: " + threadId));
        return threadMap(thread);
    }

    /** {@code run/start}：启动一次运行并返回运行编号与初始状态。 */
    private Map<String, Object> runStart(JsonNode params) throws Exception {
        String threadId = required(params, "thread_id");
        String mode = textOr(params, "mode", "agent");
        String model = required(params, "model");
        String message = textOr(params, "message", "");
        RunRecord run = runService.startRun(threadId, Mode.fromWire(mode), model, message);
        return Map.of(
                "run_id", run.runId(),
                "thread_id", run.threadId(),
                "status", run.status().wireValue());
    }

    /** {@code run/cancel}：取消运行，运行不存在则抛出 NOT_FOUND。 */
    private Map<String, Object> runCancel(JsonNode params) {
        String runId = required(params, "run_id");
        if (runService.getRun(runId).isEmpty()) {
            throw new RpcException(NOT_FOUND, "run not found: " + runId);
        }
        runService.cancelRun(runId);
        return Map.of("run_id", runId, "status", RunStatus.CANCELLED.wireValue());
    }

    /** {@code approval/respond}：回复审批决策（allow / deny 等）。 */
    private Map<String, Object> approvalRespond(JsonNode params) {
        String approvalId = required(params, "approval_id");
        String decision = required(params, "decision");
        ApprovalDecision parsed = ApprovalDecision.fromWire(decision);
        if (!approvalService.respond(approvalId, parsed)) {
            throw new RpcException(NOT_FOUND, "approval not found: " + approvalId);
        }
        return Map.of("approval_id", approvalId, "decision", parsed.wireValue());
    }

    /**
     * {@code event/subscribe}：长轮询订阅运行事件流。
     *
     * <p>循环推送自 {@code from_seq} 之后的事件，直到运行进入终结状态且事件已消费完毕，
     * 此时返回运行状态与最后序号。
     */
    private Map<String, Object> eventSubscribe(JsonNode params, Consumer<Map<String, Object>> notificationSink)
            throws InterruptedException {
        String runId = required(params, "run_id");
        int fromSeq = params != null && params.has("from_seq") ? params.get("from_seq").asInt(0) : 0;
        if (runService.getRun(runId).isEmpty()) {
            throw new RpcException(NOT_FOUND, "run not found: " + runId);
        }

        int cursor = fromSeq;
        while (true) {
            for (Event event : store.eventStore().fromSeq(runId, cursor)) {
                Map<String, Object> note = Map.of(
                        "run_id", runId,
                        "event", ProtocolJson.mapper().convertValue(event, Map.class));
                if (notificationSink != null) {
                    notificationSink.accept(note);
                }
                cursor = event.seq() + 1;
            }

            RunStatus status = runService.liveStatus(runId);
            if (isTerminal(status) && cursor >= store.eventStore().allForRun(runId).size()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("run_id", runId);
                result.put("status", status.wireValue());
                result.put("last_seq", Math.max(0, cursor - 1));
                return result;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
    }

    /** 判断运行状态是否已终结（成功 / 失败 / 已取消）。 */
    private static boolean isTerminal(RunStatus status) {
        return status == RunStatus.SUCCEEDED || status == RunStatus.FAILED || status == RunStatus.CANCELLED;
    }

    /** 将线程记录序列化为对外返回的 Map。 */
    private static Map<String, Object> threadMap(ThreadRecord thread) {
        return Map.of(
                "thread_id", thread.threadId(),
                "workspace_root", thread.workspaceRoot().toString(),
                "status", thread.status().wireValue());
    }

    /** 读取必填参数，缺失或为空则抛出 INVALID_PARAMS。 */
    private static String required(JsonNode params, String field) {
        if (params == null || !params.has(field) || params.get(field).isNull()) {
            throw new RpcException(INVALID_PARAMS, "missing param: " + field);
        }
        String value = params.get(field).asText();
        if (value.isBlank()) {
            throw new RpcException(INVALID_PARAMS, "missing param: " + field);
        }
        return value;
    }

    /** 读取可选参数，缺省时返回默认值。 */
    private static String textOr(JsonNode params, String field, String defaultValue) {
        if (params == null || !params.has(field) || params.get(field).isNull()) {
            return defaultValue;
        }
        return params.get(field).asText(defaultValue);
    }

    /** 携带自定义错误码的运行时异常。 */
    static final class RpcException extends RuntimeException {
        private final int code;

        RpcException(int code, String message) {
            super(message);
            this.code = code;
        }

        int code() {
            return code;
        }
    }
}
