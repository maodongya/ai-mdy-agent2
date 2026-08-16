package com.anvil.core.model;

/** Token and latency metrics from a single model API call. */
public record ModelUsage(long inputTokens, long outputTokens, Long cachedTokens, long latencyMs) {

    public static ModelUsage estimate(long inputTokens, long outputTokens, long latencyMs) {
        return new ModelUsage(inputTokens, outputTokens, null, latencyMs);
    }
}
