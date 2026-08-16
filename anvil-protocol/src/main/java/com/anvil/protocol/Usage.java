package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Usage(
        @JsonProperty("input_tokens") long inputTokens,
        @JsonProperty("output_tokens") long outputTokens,
        @JsonProperty("cached_tokens") Long cachedTokens,
        @JsonProperty("cost_usd_estimate") Double costUsdEstimate,
        @JsonProperty("tool_calls") int toolCalls,
        int steps) {

    public static Usage empty() {
        return new Usage(0, 0, null, null, 0, 0);
    }
}
