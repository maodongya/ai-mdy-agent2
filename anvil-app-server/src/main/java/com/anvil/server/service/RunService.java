package com.anvil.server.service;

import com.anvil.core.loop.EditorSelection;
import com.anvil.core.loop.LoopEngine;
import com.anvil.core.loop.LoopOptions;
import com.anvil.core.loop.LoopResult;
import com.anvil.core.loop.RunProfile;
import com.anvil.core.loop.RunRequest;
import com.anvil.core.model.LlmRegistry;
import com.anvil.core.model.ModelProvider;
import com.anvil.core.model.ModelProviderFactory;
import com.anvil.core.mcp.McpBridge;
import com.anvil.core.tools.ToolCatalog;
import com.anvil.protocol.Event;
import com.anvil.protocol.Mode;
import com.anvil.protocol.RunStatus;
import com.anvil.protocol.SandboxTier;
import com.anvil.core.loop.ActiveRunTracker;
import com.anvil.server.config.AnvilContextConfig;
import com.anvil.server.store.InMemoryStore;
import com.anvil.server.store.RunRecord;
import com.anvil.server.store.ThreadMemoryStore;
import com.anvil.server.store.ThreadRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 运行服务：负责会话线程（Thread）与运行（Run）的生命周期管理。
 *
 * <p>核心职责：
 * <ul>
 *   <li>创建/查询会话线程</li>
 *   <li>在后台线程池中启动一次智能体运行（LoopEngine），并采集运行事件</li>
 *   <li>维护运行实时状态（含审批等待状态）</li>
 *   <li>取消运行、查询运行状态</li>
 * </ul>
 */
@Service
public class RunService {

    /** 内存存储（线程、运行记录、事件流）。 */
    private final InMemoryStore store;
    /** 审批服务，用于权限确认。 */
    private final ApprovalService approvalService;
    /** LLM 模型注册表。 */
    private final LlmRegistry llmRegistry;
    /** MCP 桥接器。 */
    private final McpBridge mcpBridge;
    /** 沙箱安全级别。 */
    private final SandboxTier sandboxTier;
    /** 线程会话记忆存储。 */
    private final ThreadMemoryStore threadMemory;
    /** 服务器级上下文配置。 */
    private final AnvilContextConfig contextConfig;
    /** 工作区索引服务。 */
    private final WorkspaceIndexService indexService;
    /** 虚拟线程执行器，用于并行调度运行。 */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    /** 运行 id → 实时状态 的映射。 */
    private final Map<String, RunStatus> liveStatus = new ConcurrentHashMap<>();

    /** 审批超时时间（毫秒）。 */
    @Value("${anvil.approval-timeout-ms:1800000}")
    private long approvalTimeoutMs;

    /** shell 工具执行超时（毫秒）。 */
    @Value("${anvil.sandbox.shell-timeout-ms:120000}")
    private long shellTimeoutMs;

    /** 是否自动批准补丁类写工具。 */
    @Value("${anvil.policy.auto-approve-patch-tools:true}")
    private boolean autoApprovePatchTools;

    /** fixtures（脚本模型等）根目录。 */
    @Value("${anvil.fixtures-root:fixtures/models}")
    private String fixturesRoot;

    /**
     * 构造函数：注入运行所需的各依赖服务。
     *
     * @param store            内存存储
     * @param approvalService  审批服务
     * @param llmRegistry      LLM 模型注册表
     * @param mcpBridge        MCP 桥接器
     * @param sandboxTier      沙箱安全级别
     * @param threadMemory     线程记忆存储
     * @param contextConfig    服务器级上下文配置
     * @param indexService     工作区索引服务
     */
    public RunService(
            InMemoryStore store,
            ApprovalService approvalService,
            LlmRegistry llmRegistry,
            McpBridge mcpBridge,
            SandboxTier sandboxTier,
            ThreadMemoryStore threadMemory,
            AnvilContextConfig contextConfig,
            WorkspaceIndexService indexService) {
        this.store = store;
        this.approvalService = approvalService;
        this.llmRegistry = llmRegistry;
        this.mcpBridge = mcpBridge;
        this.sandboxTier = sandboxTier;
        this.threadMemory = threadMemory;
        this.contextConfig = contextConfig;
        this.indexService = indexService;
    }

    /** 创建以指定工作区根目录为上下文的会话线程。 */
    public ThreadRecord createThread(Path workspaceRoot) {
        return store.createThread(workspaceRoot);
    }

    /** 按 id 查询会话线程。 */
    public Optional<ThreadRecord> getThread(String threadId) {
        return store.thread(threadId);
    }

    /** 使用默认运行档案启动一次运行（快捷重载）。 */
    public RunRecord startRun(String threadId, Mode mode, String model, String userMessage) throws Exception {
        return startRun(threadId, mode, model, userMessage, null, null);
    }

    /** 启动一次运行（带运行档案与步数覆盖，快捷重载）。 */
    public RunRecord startRun(
            String threadId,
            Mode mode,
            String model,
            String userMessage,
            RunProfile profile,
            Integer maxStepsOverride)
            throws Exception {
        return startRun(threadId, mode, model, userMessage, profile, maxStepsOverride, List.of(), null, null);
    }

    /** 启动一次运行（带已打开文件与聚焦文件，快捷重载）。 */
    public RunRecord startRun(
            String threadId,
            Mode mode,
            String model,
            String userMessage,
            RunProfile profile,
            Integer maxStepsOverride,
            List<String> openFiles,
            String focusFile)
            throws Exception {
        return startRun(threadId, mode, model, userMessage, profile, maxStepsOverride, openFiles, focusFile, null);
    }

    /** 启动一次运行（带编辑器选区，快捷重载）。 */
    public RunRecord startRun(
            String threadId,
            Mode mode,
            String model,
            String userMessage,
            RunProfile profile,
            Integer maxStepsOverride,
            List<String> openFiles,
            String focusFile,
            EditorSelection selection)
            throws Exception {
        return startRun(
                threadId,
                mode,
                model,
                userMessage,
                profile,
                maxStepsOverride,
                openFiles,
                focusFile,
                selection,
                null);
    }

    /**
     * 启动一次运行的完整入口。
     *
     * <p>该方法是异步的：校验线程存在后立即创建运行记录并放入后台虚拟线程执行，
     * LoopEngine 完成后将结果写回线程记忆。审批等待 / 恢复会在事件回调中同步运行状态。
     *
     * @param threadId           会话线程 id
     * @param mode               运行模式
     * @param model              模型标识
     * @param userMessage        用户消息
     * @param profile            运行档案（可为 null，使用默认）
     * @param maxStepsOverride   步数覆盖（>0 时使用该值）
     * @param openFiles          已打开文件列表
     * @param focusFile          聚焦文件
     * @param selection          编辑器选区
     * @param autoApproveWrites  是否自动批准写操作（可为 null 表示不强制）
     * @return 已创建、状态为 RUNNING 的运行记录
     */
    public RunRecord startRun(
            String threadId,
            Mode mode,
            String model,
            String userMessage,
            RunProfile profile,
            Integer maxStepsOverride,
            List<String> openFiles,
            String focusFile,
            EditorSelection selection,
            Boolean autoApproveWrites)
            throws Exception {
        ThreadRecord thread =
                store.thread(threadId).orElseThrow(() -> new IllegalArgumentException("thread not found: " + threadId));

        // 预热工作区索引，便于工具快速命中
        indexService.warmAsync(thread.workspaceRoot());

        // 创建运行记录并置为 RUNNING
        RunRecord run = store.createRun(threadId, mode, model, thread.workspaceRoot());
        liveStatus.put(run.runId(), RunStatus.RUNNING);
        store.updateRunStatus(run.runId(), RunStatus.RUNNING);

        RunProfile effectiveProfile = profile != null ? profile : RunProfile.defaultFor(mode);
        int steps = maxStepsOverride != null && maxStepsOverride > 0
                ? maxStepsOverride
                : Math.max(contextConfig.maxStepsDefault(), effectiveProfile.defaultMaxSteps());

        // 组装模型提供方、工具 schema 与历史记忆
        ModelProvider provider = ModelProviderFactory.create(model, llmRegistry, fixturesRoot());
        List<Map<String, Object>> toolSchemas =
                ToolCatalog.merge(ToolCatalog.builtinSchemas(mode), mcpBridge.toolSchemas());
        List<Map<String, Object>> priorHistory = threadMemory.load(threadId);

        var atRefs = RunRequest.parseAtReferences(userMessage, thread.workspaceRoot());
        String effectiveMessage = atRefs.cleanedMessage().isBlank() ? userMessage : atRefs.cleanedMessage();

        RunRequest request = new RunRequest(
                threadId,
                run.runId(),
                mode,
                model,
                effectiveMessage,
                thread.workspaceRoot(),
                steps,
                approvalTimeoutMs,
                shellTimeoutMs,
                RunRequest.formatHarnessContext(
                        thread.workspaceRoot(), userMessage, openFiles, focusFile, selection));

        boolean yoloWrites = autoApproveWrites != null && autoApproveWrites;
        LoopOptions loopOptions = new LoopOptions(
                contextConfig.budgetForProfile(effectiveProfile),
                sandboxTier,
                "main",
                toolSchemas,
                mcpBridge,
                effectiveProfile,
                autoApprovePatchTools,
                yoloWrites,
                contextConfig.verifyConfig(),
                contextConfig.loopConfig());

        // 后台执行 LoopEngine；事件通过回调持续写回事件存储
        executor.submit(() -> {
            try {
                LoopResult result = LoopEngine.run(
                        request,
                        provider,
                        approvalService,
                        loopOptions,
                        priorHistory,
                        event -> {
                            store.eventStore().append(event);
                            if ("approval.required".equals(event.type())) {
                                // 进入审批等待状态
                                liveStatus.put(run.runId(), RunStatus.WAITING_APPROVAL);
                                store.updateRunStatus(run.runId(), RunStatus.WAITING_APPROVAL);
                            } else if ("approval.resolved".equals(event.type())) {
                                // 审批完成，恢复运行
                                liveStatus.put(run.runId(), RunStatus.RUNNING);
                                store.updateRunStatus(run.runId(), RunStatus.RUNNING);
                            }
                        });
                // 写入最终状态并保存线程记忆
                liveStatus.put(run.runId(), result.status());
                store.updateRunStatus(run.runId(), result.status());
                if (!result.finalHistory().isEmpty()) {
                    threadMemory.save(threadId, result.finalHistory(), effectiveProfile.contextBudget());
                }
            } catch (Exception e) {
                // 运行异常：标记 FAILED 并写入 run.failed 事件
                liveStatus.put(run.runId(), RunStatus.FAILED);
                store.updateRunStatus(run.runId(), RunStatus.FAILED);
                store.eventStore()
                        .append(new Event(
                                "1.0",
                                threadId,
                                run.runId(),
                                nextSeq(run.runId()),
                                "run.failed",
                                java.time.Instant.now().toString(),
                                Map.of(
                                        "error",
                                        Map.of("code", "INTERNAL", "message", e.getMessage(), "retryable", false))));
            }
        });

        return run;
    }

    /** 按 id 查询运行记录。 */
    public Optional<RunRecord> getRun(String runId) {
        return store.run(runId);
    }

    /** 查询某个运行的实时状态（优先取内存中的实时映射，否则回退到存储状态）。 */
    public RunStatus liveStatus(String runId) {
        return liveStatus.getOrDefault(runId, store.run(runId).map(RunRecord::status).orElse(RunStatus.FAILED));
    }

    /** 取消指定运行，并同步更新状态为 CANCELLED。 */
    public void cancelRun(String runId) {
        ActiveRunTracker.cancel(runId);
        liveStatus.put(runId, RunStatus.CANCELLED);
        store.updateRunStatus(runId, RunStatus.CANCELLED);
    }

    /** 返回 fixtures 脚本模型根目录的绝对路径。 */
    private Path fixturesRoot() {
        return repoRoot().resolve(fixturesRoot);
    }

    /** 从当前目录向上找到仓库根目录（包含 fixtures）。 */
    private static Path repoRoot() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        if (Files.isDirectory(cwd.resolve("fixtures"))) {
            return cwd;
        }
        return cwd.getParent();
    }

    /** 计算运行事件流的下一个序号。 */
    private int nextSeq(String runId) {
        return store.eventStore().allForRun(runId).size();
    }
}
