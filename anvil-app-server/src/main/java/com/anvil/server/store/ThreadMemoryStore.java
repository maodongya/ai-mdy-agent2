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
 * <p>Phase 11: 保存时压缩并只保留摘要 + 最近 6 条，降低跨 Run replay 的 token 成本。
 */
@Component
public final class ThreadMemoryStore {

    /** 最多存储的历史消息条数（超出部分从头部截断）。 */
    private static final int MAX_STORED_MESSAGES = 512;

    /** 跨 Run replay 时保留的最近消息条数。 */
    private static final int THREAD_KEEP_RECENT = 6;

    /** SQLite 持久化助手。 */
    private final MemoryDatabase database;
    /** threadId → 压缩清理后的历史消息缓存。 */
    private final Map<String, List<Map<String, Object>>> cache = new ConcurrentHashMap<>();

    public ThreadMemoryStore(MemoryDatabase database) {
        this.database = database;
    }

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

    public void save(String threadId, List<Map<String, Object>> history, ContextBudget budget) {
        if (threadId == null || threadId.isBlank() || history == null || history.isEmpty()) {
            return;
        }
        List<Map<String, Object>> copy = new ArrayList<>(history);
        if (copy.size() > MAX_STORED_MESSAGES) {
            int start = MessageHistorySanitizer.alignTailStart(copy, copy.size() - MAX_STORED_MESSAGES);
            copy = new ArrayList<>(copy.subList(start, copy.size()));
        }
        ContextCompactor.Result compacted = ContextCompactor.compact(copy, budget == null ? ContextBudget.standard() : budget);
        List<Map<String, Object>> trimmed =
                ContextCompactor.trimForThreadMemory(compacted.messages(), THREAD_KEEP_RECENT);
        List<Map<String, Object>> stored = MessageHistorySanitizer.sanitize(new ArrayList<>(trimmed));
        cache.put(threadId, stored);
        database.save(threadId, stored);
    }

    public void clear(String threadId) {
        cache.remove(threadId);
        database.clear(threadId);
    }
}
