package com.anvil.server.store;

import com.anvil.protocol.ThreadStatus;

import java.nio.file.Path;
import java.time.Instant;

/**
 * 线程记录：一次会话（Agent 运行上下文）的元数据不可变快照。
 *
 * <p>每个线程对应一个独立的工作区根目录、一个运行状态以及创建时间。
 * 线程可承载多次 {@code run}（运行实例），它们共享同一线程的工作区上下文。</p>
 *
 * @param threadId      线程唯一标识（如 {@code thr_1}）
 * @param workspaceRoot 该线程绑定的工作区根目录绝对路径
 * @param status        线程当前运行状态（激活/空闲/关闭等）
 * @param createdAt     线程创建时间戳
 */
public record ThreadRecord(String threadId, Path workspaceRoot, ThreadStatus status, Instant createdAt) {}
