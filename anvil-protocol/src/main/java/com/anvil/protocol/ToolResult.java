package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 工具执行结果：描述一次工具调用的最终结果。
 *
 * <p>状态约定：{@code ok} 成功、{@code error} 失败、{@code cancelled} 取消、
 * {@code denied} 被审批或策略拒绝。长内容可被截断（{@code truncated}），
 * 或通过 {@code artifact_ref} 引用落盘的附件。</p>
 *
 * @param toolCallId 工具调用标识（关联请求侧的工具调用）
 * @param name       工具名称（如 fs.read / shell.exec）
 * @param status     执行状态（ok/error/cancelled/denied）
 * @param content    执行结果文本
 * @param truncated  内容是否被截断
 * @param artifactRef 附件引用（可空，如落盘文件路径）
 * @param error      失败时的错误信息（可空）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolResult(
        @JsonProperty("tool_call_id") String toolCallId,
        String name,
        String status,
        String content,
        boolean truncated,
        @JsonProperty("artifact_ref") String artifactRef,
        ErrorInfo error) {

    public ToolResult {
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new IllegalArgumentException("toolCallId is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        if (content == null) {
            content = "";
        }
    }

    /**
     * 便捷工厂：构造成功结果。
     *
     * @param toolCallId 工具调用标识
     * @param name       工具名称
     * @param content    成功输出内容
     * @return 状态为 {@code ok} 的执行结果
     */
    public static ToolResult ok(String toolCallId, String name, String content) {
        return new ToolResult(toolCallId, name, "ok", content, false, null, null);
    }

    /**
     * 便捷工厂：构造被拒绝的结果。
     *
     * @param toolCallId 工具调用标识
     * @param name       工具名称
     * @param error      拒绝原因
     * @return 状态为 {@code denied} 的执行结果
     */
    public static ToolResult denied(String toolCallId, String name, ErrorInfo error) {
        return new ToolResult(toolCallId, name, "denied", "", false, null, error);
    }
}
