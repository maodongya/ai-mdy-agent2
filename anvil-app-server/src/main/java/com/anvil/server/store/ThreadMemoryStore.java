package com.anvil.server.store;

import com.anvil.core.compact.ContextBudget;
import com.anvil.core.compact.ContextCompactor;
import com.anvil.core.compact.MessageHistorySanitizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 线程历史内存存储：跨多个运行保存每个线程的会话历史（内存缓存 + SQLite 持久化）。
 *
 * <p>负责三件事：
 * <ol>
 *   <li>以内存缓存提供快速读取，避免频繁查询数据库；</li>
 *   <li>保存时对历史进行截断（限制条数）、压缩（{@link ContextCompactor}）与清理（{@link MessageHistorySanitizer}），
 *       控制 token 占用并去除敏感/低价值信息；</li>
 *   <li>串通 {@link MemoryDatabase} 将压缩清理后的历史落盘，实现跨进程重启的恢复能力。</li>
 * </ol>
 */
@Component
public final class ThreadMemoryStore {

    /** 最多存储的历史消息条数（超出部分从头部截断）。 */
    private static final int MAX_STORED_MESSAGES = 512;

    /** SQLite 持久化助手。 */
    private final MemoryDatabase database;
    /** threadId → 压缩清理后的历史消息缓存。 */
    private final Map<String, List<Map<String, Object>>> cache = new ConcurrentHashMap<>();

    /**
     * 构造线程历史存储。
     *
     * @param database SQLite 持久化助手
     */
    public ThreadMemoryStore(MemoryDatabase database) {
        this.database = database;
    }

    /**
     * 加载指定线程的历史消息。
     *
     * @param threadId 线程标识
     * @return 历史消息列表（不可变视图）；无记录时返回空列表
     */
    public List<Map<String, Object>> load(String threadId) {
        List<Map<String, Object>> cached = cache.get(threadId);
        if (cached != null && !cached.isEmpty()) {
            return List.copyOf(cached);
        }
        List<Map<String, Object>> fromDb = database.load(threadId);
        if (!fromDb.isEmpty()) {
            cache.put(threadId, new ArrayList<>(fromDb));
        }
        return fromDb;
    }

    /**
     * 保存指定线程的历史消息：先截断至上限，再按预算压缩，
     * 清理后同时写入内存缓存与数据库。
     *
     * @param threadId 线程标识
     * @param history  待保存的完整历史消息
     * @param budget   上下文预算（用于压缩决策）；
     *                 传入 {@code null} 时退化为 {@link ContextBudget#extended()}
     */
    public void save(String threadId, List<Map<String, Object>> history, ContextBudget budget) {
        if (threadId == null || threadId.isBlank() || history == null || history.isEmpty()) {
            return;
        }
        List<Map<String, Object>> copy = new ArrayList<>(history);
        if (copy.size() > MAX_STORED_MESSAGES) {
            // 从保留的尾部窗口对齐起始索引，优先保留靠近结尾的重要上下文
            int start = MessageHistorySanitizer.alignTailStart(copy, copy.size() - MAX_STORED_MESSAGES);
            copy = new ArrayList<>(copy.subList(start, copy.size()));
        }
        ContextCompactor.Result compacted = ContextCompactor.compact(copy, budget == null ? ContextBudget.extended() : budget);
        List<Map<String, Object>> stored = MessageHistorySanitizer.sanitize(new ArrayList<>(compacted.messages()));
        cache.put(threadId, stored);
        database.save(threadId, stored);
    }

    /**
     * 清除指定线程的历史（内存缓存与数据库一并删除）。
     *
     * @param threadId 线程标识
     */
    public void clear(String threadId) {
        cache.remove(threadId);
        database.clear(threadId);
    }
}
