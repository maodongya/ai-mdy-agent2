package com.anvil.server.api;

import com.anvil.protocol.ProtocolJson;
import com.anvil.protocol.TerminalEvent;
import com.anvil.server.service.RunService;
import com.anvil.server.store.ThreadRecord;
import com.anvil.server.terminal.TerminalSessionManager;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 终端（Terminal）控制器（M1）。
 *
 * <p>提供终端会话的创建、命令执行、终止与 SSE 事件订阅能力。</p>
 */
@RestController
@RequestMapping("/v1/terminal")
public class TerminalController {

    private final TerminalSessionManager manager;
    private final RunService runService;

    /**
     * 构造函数。
     *
     * @param manager    终端会话管理器
     * @param runService 运行服务（用于按线程解析工作目录）
     */
    public TerminalController(TerminalSessionManager manager, RunService runService) {
        this.manager = manager;
        this.runService = runService;
    }

    /**
     * 新建终端会话。
     *
     * @param body 请求体：{thread_id, title}
     * @return 会话元信息；线程不存在时返回 404
     */
    @PostMapping("/sessions")
    public ResponseEntity<?> create(@RequestBody CreateTerminalRequest body) {
        if (!manager.enabled()) {
            return ResponseEntity.status(503).body(Map.of("error", "terminal disabled"));
        }
        ThreadRecord thread = runService.getThread(body.thread_id()).orElse(null);
        if (thread == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            TerminalSessionManager.TerminalSession session =
                    manager.create(thread, body.title() == null ? "bash" : body.title());
            return ResponseEntity.ok(Map.of(
                    "session_id", session.id(),
                    "cwd", session.cwd().toString(),
                    "status", session.statusId()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 执行命令。
     *
     * @param sessionId 会话 ID
     * @param body      请求体：{command, timeout_ms}
     * @return 接受状态；会话不存在或忙时返回 400/404
     */
    @PostMapping("/sessions/{id}/exec")
    public ResponseEntity<?> exec(@PathVariable("id") String sessionId, @RequestBody ExecRequest body) {
        var session = manager.get(sessionId);
        if (session.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (body.command() == null || body.command().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "command is required"));
        }
        try {
            String jobId = manager.exec(sessionId, body.command(), body.timeout_ms() == null ? 0 : body.timeout_ms());
            return ResponseEntity.accepted().body(Map.of(
                    "session_id", sessionId, "accepted", true, "job_id", jobId, "status", "RUNNING"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 终止当前命令。
     *
     * @param sessionId 会话 ID
     * @return 状态
     */
    @PostMapping("/sessions/{id}/stop")
    public ResponseEntity<?> stop(@PathVariable("id") String sessionId) {
        var session = manager.get(sessionId);
        if (session.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        manager.stop(sessionId);
        return ResponseEntity.ok(Map.of("session_id", sessionId, "status", "IDLE"));
    }

    /**
     * SSE 订阅会话事件流。
     *
     * @param sessionId 会话 ID
     * @param fromSeq   起始事件序号
     * @return SSE 发射器
     */
    @GetMapping("/sessions/{id}/events")
    public SseEmitter events(
            @PathVariable("id") String sessionId,
            @RequestParam(name = "from_seq", defaultValue = "0") long fromSeq) {
        SseEmitter emitter = new SseEmitter(0L);
        var executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "term-sse-" + sessionId);
            t.setDaemon(true);
            return t;
        });
        executor.submit(() -> {
            try {
                long cursor = fromSeq;
                long lastHeartbeatMs = System.currentTimeMillis();
                while (true) {
                    List<TerminalEvent> batch = manager.eventsSince(sessionId, cursor);
                    for (TerminalEvent e : batch) {
                        Map<String, Object> note = Map.of(
                                "jsonrpc", "2.0",
                                "method", "terminal/notification",
                                "params", Map.of(
                                        "type", e.type(),
                                        "session_id", e.sessionId(),
                                        "seq", e.seq(),
                                        "payload", e.payload()));
                        emitter.send(SseEmitter.event().name("event").data(ProtocolJson.toJson(note)));
                        cursor = e.seq();
                        lastHeartbeatMs = System.currentTimeMillis();
                    }
                    var session = manager.get(sessionId);
                    if (session.isEmpty()) {
                        emitter.complete();
                        break;
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastHeartbeatMs >= 25_000) {
                        emitter.send(SseEmitter.event().comment("heartbeat"));
                        lastHeartbeatMs = now;
                    }
                    TimeUnit.MILLISECONDS.sleep(50);
                }
            } catch (IOException e) {
                emitter.completeWithError(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                emitter.completeWithError(e);
            } finally {
                executor.shutdownNow();
            }
        });
        return emitter;
    }

    /** 创建终端会话的请求体。 */
    public record CreateTerminalRequest(String thread_id, String title) {}

    /** 执行命令的请求体。 */
    public record ExecRequest(String command, Long timeout_ms) {}
}
