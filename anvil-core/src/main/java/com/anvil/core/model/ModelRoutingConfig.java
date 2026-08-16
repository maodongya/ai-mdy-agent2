package com.anvil.core.model;

/** Configuration for per-step model routing (Phase 9.4). */
public record ModelRoutingConfig(
        boolean enabled,
        String exploreModel,
        String editModel,
        String planModel) {

    public static ModelRoutingConfig disabled() {
        return new ModelRoutingConfig(false, "", "", "");
    }

    public static ModelRoutingConfig deepSeekDefaults(boolean enabled) {
        return new ModelRoutingConfig(
                enabled,
                "deepseek:deepseek-chat",
                "deepseek:deepseek-chat",
                "deepseek:deepseek-reasoner");
    }

    public static ModelRoutingConfig openAiDefaults(boolean enabled) {
        return new ModelRoutingConfig(
                enabled,
                "openai:gpt-4o-mini",
                "openai:gpt-4o",
                "openai:gpt-4o");
    }
}
