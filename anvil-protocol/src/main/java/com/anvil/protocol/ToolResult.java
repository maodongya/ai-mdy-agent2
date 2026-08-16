package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolResult(
        @JsonProperty("tool_call_id") String toolCallId,
        String name,
        String status,
        String content,
        boolean truncated,
        @JsonProperty("artifact_ref") String artifactRef,
        ErrorInfo error) {

    public ToolResult {
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new IllegalArgumentException("toolCallId is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        if (content == null) {
            content = "";
        }
    }

    /** status: ok | error | cancelled | denied */
    public static ToolResult ok(String toolCallId, String name, String content) {
        return new ToolResult(toolCallId, name, "ok", content, false, null, null);
    }

    public static ToolResult denied(String toolCallId, String name, ErrorInfo error) {
        return new ToolResult(toolCallId, name, "denied", "", false, null, error);
    }
}
