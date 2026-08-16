package com.anvil.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelProviderFactoryTest {

    @Test
    void deepseekPrefixUsesDeepSeekBaseUrl() throws Exception {
        LlmRegistry registry = new LlmRegistry(
                new OpenAiConfig(OpenAiConfig.OPENAI_BASE_URL, "openai-key", "gpt-4o-mini", null),
                new OpenAiConfig(OpenAiConfig.DEEPSEEK_BASE_URL, "deepseek-key", "deepseek-chat", null));

        ModelProvider provider = ModelProviderFactory.create(
                "deepseek:deepseek-reasoner", registry, java.nio.file.Path.of("fixtures/models"));

        assertInstanceOf(OpenAiModelProvider.class, provider);
        OpenAiConfig resolved = registry.resolve("deepseek:deepseek-reasoner");
        assertEquals(OpenAiConfig.DEEPSEEK_BASE_URL, resolved.baseUrl());
        assertEquals("deepseek-key", resolved.apiKey());
        assertEquals("deepseek-reasoner", resolved.model());
        assertEquals("DeepSeek", resolved.providerLabel());
    }

    @Test
    void bareDeepSeekModelIdUsesDeepSeekBaseUrl() throws Exception {
        LlmRegistry registry = new LlmRegistry(
                new OpenAiConfig(OpenAiConfig.OPENAI_BASE_URL, "openai-key", "gpt-4o-mini", null),
                new OpenAiConfig(OpenAiConfig.DEEPSEEK_BASE_URL, "deepseek-key", "deepseek-chat", null));

        OpenAiConfig resolved = registry.resolve("deepseek-chat");
        assertEquals(OpenAiConfig.DEEPSEEK_BASE_URL, resolved.baseUrl());
        assertEquals("deepseek-key", resolved.apiKey());
        assertEquals("deepseek-chat", resolved.model());
    }

    @Test
    void openaiPrefixUsesOpenAiBaseUrl() throws Exception {
        LlmRegistry registry = new LlmRegistry(
                new OpenAiConfig(OpenAiConfig.OPENAI_BASE_URL, "openai-key", "gpt-4o-mini", null),
                new OpenAiConfig(OpenAiConfig.DEEPSEEK_BASE_URL, "deepseek-key", "deepseek-chat", null));

        OpenAiConfig resolved = registry.resolve("openai:gpt-4o");
        assertEquals(OpenAiConfig.OPENAI_BASE_URL, resolved.baseUrl());
        assertEquals("openai-key", resolved.apiKey());
        assertEquals("gpt-4o", resolved.model());
        assertTrue(resolved.providerLabel().contains("OpenAI"));
    }
}
