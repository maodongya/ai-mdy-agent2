package com.anvil.core.model;

import com.anvil.core.config.EnvSecrets;

import java.time.Duration;

public record OpenAiConfig(String baseUrl, String apiKey, String model, Duration timeout) {

    public static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    public static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com/v1";

    public OpenAiConfig {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = OPENAI_BASE_URL;
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(120);
        }
    }

    public static OpenAiConfig fromEnv(String model, String apiKeyEnv) {
        return fromEnv(model, apiKeyEnv, OPENAI_BASE_URL);
    }

    public static OpenAiConfig fromEnv(String model, String apiKeyEnv, String baseUrl) {
        String envName = apiKeyEnv == null || apiKeyEnv.isBlank() ? "OPENAI_API_KEY" : apiKeyEnv;
        String key = EnvSecrets.get(envName);
        return new OpenAiConfig(baseUrl, key, model, Duration.ofSeconds(120));
    }

    public OpenAiConfig withModel(String model) {
        return new OpenAiConfig(baseUrl, apiKey, model, timeout);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** DeepSeek rejects tool names outside {@code ^[a-zA-Z0-9_-]+$} (no dots). */
    public boolean strictToolNames() {
        return baseUrl != null && baseUrl.contains("deepseek");
    }

    /** OpenAI-only streaming usage chunk; DeepSeek rejects unknown fields on some deployments. */
    public boolean supportsStreamUsageOption() {
        return baseUrl != null && baseUrl.contains("openai");
    }

    public String providerLabel() {
        if (baseUrl.contains("deepseek")) {
            return "DeepSeek";
        }
        if (baseUrl.contains("openai")) {
            return "OpenAI";
        }
        return "LLM";
    }
}
