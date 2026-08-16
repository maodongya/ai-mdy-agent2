package com.anvil.core.model;

import com.anvil.protocol.ProtocolJson;

import java.util.List;
import java.util.Map;

public record ModelTurn(String messageText, List<ToolCallIntent> toolCalls, ModelUsage usage, String reasoningContent) {

    public ModelTurn(String messageText, List<ToolCallIntent> toolCalls) {
        this(messageText, toolCalls, null, null);
    }

    public ModelTurn(String messageText, List<ToolCallIntent> toolCalls, ModelUsage usage) {
        this(messageText, toolCalls, usage, null);
    }

    public boolean isMessage() {
        return messageText != null && !messageText.isBlank();
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /** OpenAI/DeepSeek assistant message for conversation history. */
    public Map<String, Object> toHistoryMessage() {
        if (!hasToolCalls()) {
            Map<String, Object> msg = new java.util.LinkedHashMap<>();
            msg.put("role", "assistant");
            msg.put("content", messageText == null ? "" : messageText);
            if (reasoningContent != null && !reasoningContent.isBlank()) {
                msg.put("reasoning_content", reasoningContent);
            }
            return msg;
        }
        List<Map<String, Object>> toolCallsPayload = new java.util.ArrayList<>();
        for (ToolCallIntent call : toolCalls) {
            Map<String, Object> fn = new java.util.LinkedHashMap<>();
            fn.put("name", call.name());
            fn.put(
                    "arguments",
                    ProtocolJson.toJson(call.arguments() == null ? Map.of() : call.arguments()));
            toolCallsPayload.add(Map.of("id", call.id(), "type", "function", "function", fn));
        }
        Map<String, Object> assistant = new java.util.LinkedHashMap<>();
        assistant.put("role", "assistant");
        assistant.put("content", messageText == null ? "" : messageText);
        assistant.put("tool_calls", toolCallsPayload);
        if (reasoningContent != null && !reasoningContent.isBlank()) {
            assistant.put("reasoning_content", reasoningContent);
        }
        return assistant;
    }
}
