package com.anvil.server.store;

import com.anvil.protocol.Mode;
import com.anvil.protocol.RunStatus;

import java.nio.file.Path;
import java.time.Instant;

/**
 * 运行记录：一次 Agent 运行实例的不可变元数据快照。
 *
 * <p>运行实例隶属于某个线程（由 {@link #threadId()} 关联），
 * 携带运行模式、模型标识以及运行期间的实时状态。</p>
 *
 * @param runId         运行实例唯一标识（如 {@code run_1}）
 * @param threadId      所属线程标识
 * @param mode          运行模式（交互 / 自动 / 审批等，见 {@link Mode}）
 * @param model         使用的模型标识（OpenAI / DeepSeek 等）
 * @param workspaceRoot 运行绑定的工作区根目录绝对路径
 * @param status        运行当前状态（排队/运行中/完成/失败等）
 * @param createdAt     运行创建时间戳
 */
public record RunRecord(
        String runId,
        String threadId,
        Mode mode,
        String model,
        Path workspaceRoot,
        RunStatus status,
        Instant createdAt) {}
