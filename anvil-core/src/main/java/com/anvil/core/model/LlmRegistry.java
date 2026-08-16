package com.anvil.core.model;

import java.time.Duration;

/** Defaults for OpenAI-compatible LLM backends (OpenAI, DeepSeek, …). */
public record LlmRegistry(OpenAiConfig openAi, OpenAiConfig deepSeek) {

    public LlmRegistry {
        if (openAi == null) {
            openAi = OpenAiConfig.fromEnv("gpt-4o-mini", "OPENAI_API_KEY", OpenAiConfig.OPENAI_BASE_URL);
        }
        if (deepSeek == null) {
            deepSeek = OpenAiConfig.fromEnv("deepseek-chat", "DEEPSEEK_API_KEY", OpenAiConfig.DEEPSEEK_BASE_URL);
        }
    }

    public static LlmRegistry of(OpenAiConfig openAiDefaults) {
        return new LlmRegistry(openAiDefaults, null);
    }

    public static LlmRegistry fromEnv() {
        return new LlmRegistry(null, null);
    }

    public OpenAiConfig resolve(String modelId) {
        if (modelId != null && modelId.startsWith("deepseek:")) {
            String model = modelId.substring("deepseek:".length());
            return deepSeek.withModel(model);
        }
        if (modelId != null && modelId.startsWith("openai:")) {
            String model = modelId.substring("openai:".length());
            return openAi.withModel(model);
        }
        if (modelId != null && modelId.startsWith("deepseek-")) {
            return deepSeek.withModel(modelId);
        }
        return openAi.withModel(modelId);
    }
}
