package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 用量统计：记录一次运行期间的 token、耗时与成本估算。
 *
 * @param inputTokens     输入 token 数
 * @param outputTokens    输出 token 数
 * @param cachedTokens    缓存命中的 token 数（可空）
 * @param costUsdEstimate 预估成本（美元，可空）
 * @param toolCalls       工具调用次数
 * @param steps           Agent 迭代步数
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Usage(
        @JsonProperty("input_tokens") long inputTokens,
        @JsonProperty("output_tokens") long outputTokens,
        @JsonProperty("cached_tokens") Long cachedTokens,
        @JsonProperty("cost_usd_estimate") Double costUsdEstimate,
        @JsonProperty("tool_calls") int toolCalls,
        int steps) {

    /**
     * 返回全零的空白用量统计（作为缺失计数的占位）。
     *
     * @return 空白用量
     */
    public static Usage empty() {
        return new Usage(0, 0, null, null, 0, 0);
    }
}
