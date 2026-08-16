package com.anvil.ui;

import java.util.List;

final class ModelPresets {
    private ModelPresets() {}

    static List<String> all() {
        return List.of(
                "deepseek:deepseek-chat",
                "deepseek:deepseek-reasoner",
                "openai:gpt-4o-mini",
                "openai:gpt-4o",
                "scripted:read-add",
                "scripted:agent-write-approve");
    }
}
