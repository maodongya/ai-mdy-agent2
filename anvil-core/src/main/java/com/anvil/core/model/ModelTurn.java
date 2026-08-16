package com.anvil.core.model;

import java.util.List;

public record ModelTurn(String messageText, List<ToolCallIntent> toolCalls, ModelUsage usage) {

    public ModelTurn(String messageText, List<ToolCallIntent> toolCalls) {
        this(messageText, toolCalls, null);
    }

    public boolean isMessage() {
        return messageText != null && !messageText.isBlank();
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
