package com.anvil.server.api;

import com.anvil.server.service.RunService;
import com.anvil.server.store.ThreadRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Map;

/**
 * REST 入口，负责线程（Thread）的创建与查询。
 *
 * <p>线程是 Anvil 中一次对话/任务执行的载体，持有独立的 workspace 工作目录与运行状态。</p>
 *
 * <p>本控制器仅暴露 REST 接口，实际逻辑委托给 {@link RunService}。</p>
 *
 * <p>对外暴露的 REST 端点：</p>
 * <ul>
 *   <li>{@code POST /v1/threads} —— 创建一个新线程；</li>
 *   <li>{@code GET  /v1/threads/{id}} —— 按线程 ID 查询其元信息。</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/threads")
public class ThreadController {

    private final RunService runService;

    /**
     * 通过构造器注入 {@link RunService}（Spring 管理 Bean 生命周期）。
     *
     * @param runService 线程/运行相关的服务层组件
     */
    public ThreadController(RunService runService) {
        this.runService = runService;
    }

    /**
     * 创建一个新线程。
     *
     * <p>请求体中的 {@code cwd} 为可选的初始工作目录；若为空或空白字符串，则回退为
     * 当前工作目录（"."）。目录会被解析为绝对路径并做规范化。</p>
     *
     * <p>成功时返回 200，响应体包含：</p>
     * <ul>
     *   <li>{@code thread_id} —— 新线程的标识符；</li>
     *   <li>{@code workspace_root} —— 线程的绝对工作目录路径；</li>
     *   <li>{@code status} —— 线程当前状态（wire 格式）。</li>
     * </ul>
     *
     * @param body 创建线程的请求体（{@link CreateThreadRequest}）
     * @return HTTP 响应，包含新建线程的元信息
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateThreadRequest body) {
        // 将请求中的 cwd 归一化：为空或空白时回退到当前目录 "."，随后解析为绝对路径并规范化
        Path cwd = Path.of(body.cwd() == null || body.cwd().isBlank() ? "." : body.cwd());
        // 委托给服务层创建线程记录
        ThreadRecord thread = runService.createThread(cwd.toAbsolutePath().normalize());
        // 组装并返回线程的元信息（线程 ID、工作目录、状态）
        return ResponseEntity.ok(Map.<String, Object>of(
                "thread_id", thread.threadId(),
                "workspace_root", thread.workspaceRoot().toString(),
                "status", thread.status().wireValue()));
    }

    /**
     * 按 ID 查询一个线程。
     *
     * <p>若线程存在，返回与 {@link #create(CreateThreadRequest)} 相同结构的元信息；
     * 若不存在则返回 404 Not Found。</p>
     *
     * @param id 线程 ID（路径变量）
     * @return 线程存在时为 HTTP 200 及线程元信息；否则为 HTTP 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable("id") String id) {
        // 若线程存在则构造成功响应，否则返回 404
        return runService
                .getThread(id)
                .map(t -> ResponseEntity.ok(Map.<String, Object>of(
                        "thread_id", t.threadId(),
                        "workspace_root", t.workspaceRoot().toString(),
                        "status", t.status().wireValue())))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 创建线程的请求体 DTO（value record）。
     *
     * <p>由 Spring 负责从 HTTP 请求体 JSON 自动反序列化绑定。</p>
     *
     * @param cwd 可选的初始工作目录；为空时由服务端回退为当前目录
     */
    public record CreateThreadRequest(String cwd) {}
}
