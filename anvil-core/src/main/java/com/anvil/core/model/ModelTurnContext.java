package com.anvil.core.model;

import com.anvil.core.prompt.PromptBundle;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public record ModelTurnContext(List<Map<String, Object>> history, PromptBundle prompt, Consumer<String> onTextDelta) {

    public ModelTurnContext(List<Map<String, Object>> history, PromptBundle prompt) {
        this(history, prompt, null);
    }
}
