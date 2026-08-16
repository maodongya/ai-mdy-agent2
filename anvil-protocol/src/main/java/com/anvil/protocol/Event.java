package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Event(
        @JsonProperty("protocol_version") String protocolVersion,
        @JsonProperty("thread_id") String threadId,
        @JsonProperty("run_id") String runId,
        int seq,
        String type,
        String ts,
        Map<String, Object> payload) {

    public Event {
        if (threadId == null || threadId.isBlank()) {
            throw new IllegalArgumentException("threadId is required");
        }
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId is required");
        }
        if (seq < 0) {
            throw new IllegalArgumentException("seq must be >= 0");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type is required");
        }
        if (ts == null || ts.isBlank()) {
            throw new IllegalArgumentException("ts is required");
        }
        if (protocolVersion == null || protocolVersion.isBlank()) {
            protocolVersion = ProtocolConstants.PROTOCOL_VERSION;
        }
        if (payload == null) {
            payload = Collections.emptyMap();
        } else {
            payload = Map.copyOf(payload);
        }
    }
}
