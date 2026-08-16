package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Map;

/**
 * 运行过程中的事件记录：Agent 会话产生的一切可观测事件
 * （消息增量、工具调用、审批请求、运行终态等）统一以该结构表示，
 * 通过 SSE 流式推送给前端。
 *
 * @param protocolVersion 协议版本（默认取 {@link ProtocolConstants#PROTOCOL_VERSION}）
 * @param threadId        所属线程标识
 * @param runId           所属运行实例标识
 * @param seq             事件序号（单调递增，用于增量消费）
 * @param type            事件类型（如 message.delta / tool.completed / approval.required …）
 * @param ts              事件时间戳
 * @param payload         事件负载（随类型不同而各异）
 */
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
