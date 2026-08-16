package com.anvil.server.api;

import com.anvil.protocol.Event;
import com.anvil.core.loop.EditorSelection;
import com.anvil.core.loop.RunProfile;
import com.anvil.protocol.Mode;
import com.anvil.protocol.ProtocolJson;
import com.anvil.server.service.RunService;
import com.anvil.server.store.RunRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 运行（Run）控制器 —— 负责处理 Agent 运行任务的创建、事件流订阅和取消操作。
 * <p>
 * 所有端点均位于 {@code /v1} 路径之下，涵盖以下能力：
 * <ul>
 *     <li>在指定线程中启动一个新的运行任务</li>
 *     <li>通过 SSE（Server-Sent Events）实时推送运行过程中的事件</li>
 *     <li>取消正在进行的运行任务</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1")
public class RunController {

    /** 运行服务：负责运行任务的创建、状态查询与取消等业务逻辑 */
    private final RunService runService;

    /** 内存存储：用于读取运行过程产生的事件流 */
    private final com.anvil.server.store.InMemoryStore store;

    /**
     * 构造函数 —— 由 Spring 容器注入依赖。
     *
     * @param runService 运行服务实例
     * @param store      内存存储实例，用于事件查询
     */
    public RunController(RunService runService, com.anvil.server.store.InMemoryStore store) {
        this.runService = runService;
        this.store = store;
    }

    /**
     * 启动一个运行任务。
     * <p>
     * 客户端向指定的线程（thread）提交一条消息，服务端据此创建并启动一个运行任务，
     * 返回任务 ID、线程 ID 和初始状态。
     *
     * @param threadId 要运行所在线程的 ID（路径参数）
     * @param body     请求体，包含模式、模型、消息、运行配置和最大步数等字段
     * @return HTTP 202 Accepted，响应体包含 run_id、thread_id 和 status
     * @throws Exception 启动运行时可能抛出的异常
     */
    @PostMapping("/threads/{threadId}/runs")
    public ResponseEntity<Map<String, Object>> start(
            @PathVariable("threadId") String threadId, @RequestBody StartRunRequest body) throws Exception {
        // 解析运行模式；若请求中未指定，默认使用 "agent" 模式
        Mode mode = Mode.fromWire(body.mode() == null ? "agent" : body.mode());
        // 解析运行配置（profile）
        RunProfile profile = RunProfile.fromWire(body.profile());
        // 调用运行服务创建并启动运行任务
        RunRecord run = runService.startRun(
                threadId,
                mode,
                body.model(),
                body.message(),
                profile,
                body.maxSteps(),
                body.openFiles(),
                body.focusFile(),
                editorSelection(body),
                body.autoApproveWrites());
        // 返回 202 接受状态，并携带运行任务的标识与初始状态
        return ResponseEntity.accepted()
                .body(Map.of(
                        "run_id", run.runId(),
                        "thread_id", run.threadId(),
                        "status", run.status().wireValue()));
    }

    /**
     * 通过 SSE 实时订阅指定运行任务的事件流。
     * <p>
     * 客户端可以指定起始序列号（from_seq），服务端将从事务日志中拉取该序号之后的新事件，
     * 以 SSE 格式逐个推送。当运行任务达到终止状态且事件全部推送完毕后，连接自动关闭。
     *
     * @param runId   运行任务 ID（路径参数）
     * @param fromSeq 起始事件序列号，默认为 0（从第一条开始）
     * @return SseEmitter SSE 发射器，持续向客户端推送事件
     */
    @GetMapping("/runs/{runId}/events")
    public SseEmitter events(@PathVariable("runId") String runId, @RequestParam(name = "from_seq", defaultValue = "0") int fromSeq) {
        // 创建永不超时的 SSE 发射器（0L 表示不设置超时）
        SseEmitter emitter = new SseEmitter(0L);
        // 使用单线程守护线程池来驱动事件推送，线程命名为 "sse-{runId}"
        var executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sse-" + runId);
            t.setDaemon(true);
            return t;
        });
        executor.submit(() -> {
            try {
                int cursor = fromSeq; // 当前事件游标
                long lastHeartbeatMs = System.currentTimeMillis();
                while (true) {
                    // 从事件存储中拉取游标之后的新批次事件
                    var batch = store.eventStore().fromSeq(runId, cursor);
                    // 逐条推送事件到客户端
                    for (Event event : batch) {
                        emitter.send(SseEmitter.event().name("event").data(ProtocolJson.toJson(event)));
                        cursor = event.seq() + 1; // 推进游标到下一个事件
                        lastHeartbeatMs = System.currentTimeMillis();
                    }
                    // 查询运行任务的实时状态
                    var status = runService.liveStatus(runId);
                    // 若任务已终止且本批次无新事件，则关闭连接
                    if (isTerminal(status) && batch.isEmpty()) {
                        emitter.complete();
                        break;
                    }
                    // 若任务已终止且所有事件均已推送完毕，则关闭连接
                    if (isTerminal(status) && cursor >= store.eventStore().allForRun(runId).size()) {
                        emitter.complete();
                        break;
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastHeartbeatMs >= 25_000) {
                        emitter.send(SseEmitter.event().comment("heartbeat"));
                        lastHeartbeatMs = now;
                    }
                    // 短暂休眠，避免忙轮询，同时让新事件有时间写入存储
                    TimeUnit.MILLISECONDS.sleep(50);
                }
            } catch (IOException e) {
                // 客户端断开等 IO 异常：以错误状态结束 SSE 连接
                emitter.completeWithError(e);
            } catch (InterruptedException e) {
                // 线程被中断：恢复中断标志并以错误状态结束
                Thread.currentThread().interrupt();
                emitter.completeWithError(e);
            } finally {
                // 无论成功或失败，均关闭线程池以释放资源
                executor.shutdownNow();
            }
        });
        return emitter;
    }

    /**
     * 取消一个正在运行的运行任务。
     *
     * @param runId 运行任务 ID（路径参数）
     * @return 若任务不存在返回 404；成功取消返回 200 及取消后的状态信息
     */
    @PostMapping("/runs/{runId}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable("runId") String runId) {
        // 若运行任务不存在，返回 404 Not Found
        if (runService.getRun(runId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        // 调用运行服务取消任务
        runService.cancelRun(runId);
        // 返回取消结果
        return ResponseEntity.ok(Map.of("run_id", runId, "status", "cancelled"));
    }

    /**
     * 判断指定运行状态是否为终止状态。
     *
     * @param status 运行状态
     * @return true 表示该状态为终止状态（成功、失败或已取消）
     */
    private static boolean isTerminal(com.anvil.protocol.RunStatus status) {
        return status == com.anvil.protocol.RunStatus.SUCCEEDED
                || status == com.anvil.protocol.RunStatus.FAILED
                || status == com.anvil.protocol.RunStatus.CANCELLED;
    }

    private static EditorSelection editorSelection(StartRunRequest body) {
        if (body.selectionText() == null || body.selectionText().isBlank()) {
            return null;
        }
        int start = body.selectionStartLine() == null ? 1 : body.selectionStartLine();
        int end = body.selectionEndLine() == null ? start : body.selectionEndLine();
        return new EditorSelection(start, end, body.selectionText());
    }

    /**
     * 启动运行请求的请求体数据结构。
     *
     * @param mode     运行模式（如 "agent"）；可空，为空时默认使用 "agent"
     * @param model    使用的模型名称（如 OpenAI / DeepSeek 标识）
     * @param message  提交给 Agent 的初始用户消息
     * @param profile  运行配置（profile）
     * @param maxSteps 最大执行步数；可空，表示使用默认限制
     */
    public record StartRunRequest(
            String mode,
            String model,
            String message,
            String profile,
            Integer maxSteps,
            java.util.List<String> openFiles,
            String focusFile,
            Integer selectionStartLine,
            Integer selectionEndLine,
            String selectionText,
            Boolean autoApproveWrites) {}
}
