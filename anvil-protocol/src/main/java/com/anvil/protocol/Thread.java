package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 线程的对外表示：描述一次会话线程的元数据。
 *
 * @param id        线程唯一标识
 * @param title     线程标题（可空）
 * @param workspace 线程绑定的工作区引用
 * @param createdAt 创建时间（可空）
 * @param updatedAt 最后更新时间（可空）
 * @param status    线程状态（激活/归档等）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Thread(
        String id,
        String title,
        WorkspaceRef workspace,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        ThreadStatus status) {

    public Thread {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (workspace == null) {
            throw new IllegalArgumentException("workspace is required");
        }
        if (status == null) {
            status = ThreadStatus.ACTIVE;
        }
    }
}
