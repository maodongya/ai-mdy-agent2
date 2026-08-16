package com.anvil.core.model;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ModelProviderFactory {

    private ModelProviderFactory() {}

    public static ModelProvider create(String modelId, OpenAiConfig openAiDefaults, Path fixturesRoot) throws Exception {
        return create(modelId, LlmRegistry.of(openAiDefaults), fixturesRoot);
    }

    public static ModelProvider create(String modelId, LlmRegistry registry, Path fixturesRoot) throws Exception {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("model id is required");
        }
        if (modelId.startsWith("scripted:")) {
            String scriptName = modelId.substring("scripted:".length()) + ".jsonl";
            Path script = fixturesRoot.resolve(scriptName);
            if (!Files.isRegularFile(script)) {
                throw new IllegalArgumentException("script not found: " + script);
            }
            return new ScriptedModel(script);
        }
        OpenAiConfig config = registry.resolve(modelId);
        return new OpenAiModelProvider(config);
    }
}
