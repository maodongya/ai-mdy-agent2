package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * 错误描述：用于运行失败、工具执行失败等场景的结构化错误信息。
 *
 * @param code      稳定错误码（见 {@link ErrorCodes}）
 * @param message   人类可读的错误消息
 * @param retryable 是否可重试
 * @param details   附加的详细字段（可空）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorInfo(
        String code,
        String message,
        boolean retryable,
        Map<String, Object> details) {

    public ErrorInfo {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        if (message == null) {
            message = "";
        }
    }

    /**
     * 便捷工厂：构造不含附加字段的错误信息。
     *
     * @param code      错误码
     * @param message   错误消息
     * @param retryable 是否可重试
     * @return 错误信息实例
     */
    public static ErrorInfo of(String code, String message, boolean retryable) {
        return new ErrorInfo(code, message, retryable, null);
    }
}
