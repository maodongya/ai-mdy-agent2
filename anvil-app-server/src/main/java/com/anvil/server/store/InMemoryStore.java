package com.anvil.server.store;

import com.anvil.protocol.Mode;
import com.anvil.protocol.RunStatus;
import com.anvil.protocol.ThreadStatus;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存存储：以并发安全的内存 Map 保存线程与运行实例记录。
 *
 * <p>提供线程 / 运行实例的增查接口，并内嵌一个 {@link EventStore}
 * 用于承载运行过程的事件数据。所有操作均线程安全，
 * 适用于单机、无持久化要求的开发与演示场景。</p>
 */
public final class InMemoryStore {

    /** 线程 ID 自增序列。 */
    private final AtomicLong threadSeq = new AtomicLong(0);
    /** 运行实例 ID 自增序列。 */
    private final AtomicLong runSeq = new AtomicLong(0);
    /** threadId → 线程记录。 */
    private final Map<String, ThreadRecord> threads = new ConcurrentHashMap<>();
    /** runId → 运行记录。 */
    private final Map<String, RunRecord> runs = new ConcurrentHashMap<>();
    /** 事件存储（共享单例）。 */
    private final EventStore eventStore = new EventStore();

    /**
     * 新建一个线程并登记到内存中。
     *
     * @param workspaceRoot 线程绑定的工作区根目录
     * @return 新建的线程记录
     */
    public ThreadRecord createThread(Path workspaceRoot) {
        String id = "thr_" + threadSeq.incrementAndGet();
        ThreadRecord record = new ThreadRecord(id, workspaceRoot, ThreadStatus.ACTIVE, Instant.now());
        threads.put(id, record);
        return record;
    }

    /**
     * 按 id 查询线程（可能为空）。
     *
     * @param id 线程标识
     * @return 线程记录 Optional
     */
    public Optional<ThreadRecord> thread(String id) {
        return Optional.ofNullable(threads.get(id));
    }

    /**
     * 在指定线程下新建一个运行实例。
     *
     * @param threadId      所属线程标识
     * @param mode          运行模式
     * @param model         模型标识
     * @param workspaceRoot 运行绑定的工作区根目录
     * @return 新建的运行记录（初始状态为 {@link RunStatus#QUEUED}）
     */
    public RunRecord createRun(String threadId, Mode mode, String model, Path workspaceRoot) {
        String id = "run_" + runSeq.incrementAndGet();
        RunRecord record = new RunRecord(id, threadId, mode, model, workspaceRoot, RunStatus.QUEUED, Instant.now());
        runs.put(id, record);
        return record;
    }

    /**
     * 更新指定运行实例的状态（保留其余字段不变）。
     *
     * @param runId  运行实例标识
     * @param status 新的运行状态
     */
    public void updateRunStatus(String runId, RunStatus status) {
        runs.computeIfPresent(runId, (id, rec) -> new RunRecord(
                rec.runId(), rec.threadId(), rec.mode(), rec.model(), rec.workspaceRoot(), status, rec.createdAt()));
    }

    /**
     * 按 id 查询运行记录（可能为空）。
     *
     * @param id 运行实例标识
     * @return 运行记录 Optional
     */
    public Optional<RunRecord> run(String id) {
        return Optional.ofNullable(runs.get(id));
    }

    /**
     * 返回共享的事件存储。
     *
     * @return {@link EventStore} 实例
     */
    public EventStore eventStore() {
        return eventStore;
    }
}
