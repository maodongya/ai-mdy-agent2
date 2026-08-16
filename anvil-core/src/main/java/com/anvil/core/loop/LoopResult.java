package com.anvil.core.loop;

import com.anvil.protocol.Event;
import com.anvil.protocol.RunStatus;

import java.util.List;
import java.util.Map;

public record LoopResult(List<Event> events, RunStatus status, List<Map<String, Object>> finalHistory) {

    public LoopResult(List<Event> events, RunStatus status) {
        this(events, status, List.of());
    }
}
