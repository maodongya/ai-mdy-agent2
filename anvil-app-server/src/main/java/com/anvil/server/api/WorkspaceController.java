package com.anvil.server.api;

import com.anvil.server.service.RunService;
import com.anvil.server.store.ThreadRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 工作区控制器。
 * <p>
 * 提供对某个线程工作区（workspace）的只读访问接口：
 * <ul>
 *     <li>查看工作区的目录树（/tree）</li>
 *     <li>读取工作区中某个文件的文本内容（/file）</li>
 *     <li>保存工作区中某个文件（PUT /file，Phase 9.1）</li>
 * </ul>
 * 所有接口都必须在请求参数中携带 thread_id，表示针对哪个线程的工作区进行操作。
 */
@RestController
@RequestMapping("/v1/workspace")
public class WorkspaceController {

    /** 运行服务，用于根据 thread_id 查询线程记录。 */
    private final RunService runService;

    /**
     * 构造器，注入运行服务。
     *
     * @param runService 运行服务
     */
    public WorkspaceController(RunService runService) {
        this.runService = runService;
    }

    /**
     * 获取工作区目录树。
     * <p>
     * 根据 thread_id 找到对应线程，若线程不存在则返回 404；
     * 否则扫描线程工作区根目录并返回一棵目录树节点列表。
     *
     * @param threadId 线程 ID（请求参数 thread_id）
     * @return 包含 nodes（目录树节点）的 JSON 响应；线程不存在时为 404
     * @throws Exception 扫描文件系统可能抛出的 IO 异常
     */
    @GetMapping("/tree")
    public ResponseEntity<?> tree(@RequestParam String thread_id) throws Exception {
        // 根据 thread_id 查询线程记录，不存在则返回 404
        ThreadRecord thread = runService
                .getThread(thread_id)
                .orElse(null);
        if (thread == null) {
            return ResponseEntity.notFound().build();
        }
        // 取线程工作区根目录并扫描其目录树
        Path root = thread.workspaceRoot();
        List<Map<String, Object>> nodes = WorkspaceScanner.scan(root);
        return ResponseEntity.ok(Map.of("thread_id", thread_id, "nodes", nodes));
    }

    /**
     * 读取工作区中某个文件的文本内容。
     * <p>
     * 根据 thread_id 找到对应线程，若线程不存在则返回 404；
     * path 为相对于工作区根的路径，解析后必须仍然位于工作区根目录内，
     * 否则视为路径越界，返回 400；若目标不是常规文件则返回 404。
     *
     * @param threadId 线程 ID（请求参数 thread_id）
     * @param path     相对于工作区根目录的文件路径
     * @return 包含 path 和 content（文件内容）的 JSON 响应
     * @throws Exception 读取文件可能抛出的 IO 异常
     */
    @GetMapping("/file")
    public ResponseEntity<?> file(@RequestParam String thread_id, @RequestParam String path) throws Exception {
        // 根据 thread_id 查询线程记录，不存在则返回 404
        ThreadRecord thread = runService
                .getThread(thread_id)
                .orElse(null);
        if (thread == null) {
            return ResponseEntity.notFound().build();
        }
        // 解析为绝对路径并规范化，防止路径越界（如 ../ 逃生）
        Path abs = thread.workspaceRoot().resolve(path).normalize();
        if (!abs.startsWith(thread.workspaceRoot())) {
            // 路径越界，拒绝访问
            return ResponseEntity.badRequest().body(Map.of("error", "path escape"));
        }
        // 仅允许读取常规文件
        if (!Files.isRegularFile(abs)) {
            return ResponseEntity.notFound().build();
        }
        // 读取文件内容并返回
        return ResponseEntity.ok(Map.of("path", path, "content", Files.readString(abs)));
    }

    /**
     * 保存工作区中某个文件的文本内容（Phase 9.1 人机共编）。
     */
    @PutMapping("/file")
    public ResponseEntity<?> saveFile(
            @RequestParam String thread_id,
            @RequestParam String path,
            @RequestBody SaveFileRequest body)
            throws Exception {
        ThreadRecord thread = runService.getThread(thread_id).orElse(null);
        if (thread == null) {
            return ResponseEntity.notFound().build();
        }
        Path abs = thread.workspaceRoot().resolve(path).normalize();
        if (!abs.startsWith(thread.workspaceRoot())) {
            return ResponseEntity.badRequest().body(Map.of("error", "path escape"));
        }
        if (body == null || body.content() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "content required"));
        }
        Path parent = abs.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(abs, body.content());
        return ResponseEntity.ok(Map.of("path", path, "saved", true, "bytes", body.content().length()));
    }

    public record SaveFileRequest(String content) {}
}
