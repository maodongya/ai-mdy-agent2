package com.anvil.server.store;

import com.anvil.protocol.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 事件存储：按时间顺序保存 Agent 运行过程中产生的事件，
 * 并支持按 runId 查询某个运行实例的完整事件列表。
 *
 * <p>内部使用 {@link CopyOnWriteArrayList} 兼顾并发写与遍历安全，
 * 又以 {@link ConcurrentHashMap} 维护 runId → 事件列表 的索引，
 * 避免每次查询全量扫描。</p>
 */
public final class EventStore {

    /** 全局顺序事件列表（追加有序）。 */
    private final CopyOnWriteArrayList<Event> events = new CopyOnWriteArrayList<>();
    /** runId → 该运行实例的事件列表索引。 */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Event>> byRun = new ConcurrentHashMap<>();

    /**
     * 追加一个事件到全局列表，同时登记到对应 runId 的索引中。
     *
     * @param event 待追加的事件
     */
    public void append(Event event) {
        events.add(event);
        byRun.computeIfAbsent(event.runId(), id -> new CopyOnWriteArrayList<>()).add(event);
    }

    /**
     * 返回某运行实例从 {@code fromSeq}（含）之后的事件列表。
     * 用于 SSE 长连接的增量拉取（事件轮询）。
     *
     * @param runId   运行实例标识
     * @param fromSeq 起始序号（从 0 开始）
     * @return 增量事件列表；无效 runId 或 fromSeq 越界时返回空列表
     */
    public List<Event> fromSeq(String runId, int fromSeq) {
        CopyOnWriteArrayList<Event> runEvents = byRun.get(runId);
        if (runEvents == null || fromSeq >= runEvents.size()) {
            return List.of();
        }
        return new ArrayList<>(runEvents.subList(fromSeq, runEvents.size()));
    }

    /**
     * 返回某运行实例的全部事件（不可变视图）。
     *
     * @param runId 运行实例标识
     * @return 该运行实例的不可变事件列表；runId 不存在时返回空列表
     */
    public List<Event> allForRun(String runId) {
        CopyOnWriteArrayList<Event> runEvents = byRun.get(runId);
        if (runEvents == null) {
            return List.of();
        }
        return List.copyOf(runEvents);
    }
}
