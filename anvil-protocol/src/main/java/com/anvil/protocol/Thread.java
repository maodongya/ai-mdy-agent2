package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Thread(
        String id,
        String title,
        WorkspaceRef workspace,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        ThreadStatus status) {

    public Thread {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (workspace == null) {
            throw new IllegalArgumentException("workspace is required");
        }
        if (status == null) {
            status = ThreadStatus.ACTIVE;
        }
    }
}
