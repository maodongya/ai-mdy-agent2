package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 运行实例的对外表示：描述一次 Agent 运行的元数据与最终结果。
 *
 * @param id       运行实例唯一标识
 * @param threadId 所属线程标识
 * @param mode     运行模式
 * @param model    使用的模型标识
 * @param status   运行状态（排队/运行/等待审批/成功/失败/取消等）
 * @param usage    运行期间的 token / 成本统计（可空）
 * @param error    运行失败时的错误信息（可空）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Run(
        String id,
        @JsonProperty("thread_id") String threadId,
        Mode mode,
        String model,
        RunStatus status,
        Usage usage,
        ErrorInfo error) {

    public Run {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (threadId == null || threadId.isBlank()) {
            throw new IllegalArgumentException("threadId is required");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode is required");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model is required");
        }
        if (status == null) {
            status = RunStatus.QUEUED;
        }
    }
}
