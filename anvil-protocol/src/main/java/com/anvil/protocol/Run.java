package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Run(
        String id,
        @JsonProperty("thread_id") String threadId,
        Mode mode,
        String model,
        RunStatus status,
        Usage usage,
        ErrorInfo error) {

    public Run {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (threadId == null || threadId.isBlank()) {
            throw new IllegalArgumentException("threadId is required");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode is required");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model is required");
        }
        if (status == null) {
            status = RunStatus.QUEUED;
        }
    }
}
