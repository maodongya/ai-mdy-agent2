package com.anvil.core.loop;

import com.anvil.core.compact.RunAnchors;
import com.anvil.protocol.ErrorCodes;
import com.anvil.protocol.Event;
import com.anvil.protocol.ProtocolConstants;
import com.anvil.protocol.RunStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.function.Consumer;

public final class RunContext {

    private final String threadId;
    private final String runId;
    private final AtomicInteger seq = new AtomicInteger(0);
    private final List<Event> events = new ArrayList<>();
    private final Set<String> sessionAllows = new HashSet<>();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final List<Map<String, Object>> history = new ArrayList<>();
    private final Consumer<Event> listener;
    private long totalInputTokens;
    private long totalOutputTokens;
    private int toolCallCount;
    private final RunAnchors anchors = new RunAnchors();
    private volatile boolean verifyFixRequired;
    private int consecutiveWriteToolFailures;
    private int verifyFixTextOnlyRetries;
    private volatile boolean abortRun;
    private volatile RunStatus abortStatus = RunStatus.FAILED;

    private static final int MAX_CONSECUTIVE_WRITE_FAILURES = 5;
    private static final int MAX_VERIFY_FIX_TEXT_RETRIES = 3;

    public RunContext(String threadId, String runId) {
        this(threadId, runId, null);
    }

    public RunContext(String threadId, String runId, Consumer<Event> listener) {
        this.threadId = threadId;
        this.runId = runId;
        this.listener = listener;
    }

    public String threadId() {
        return threadId;
    }

    public String runId() {
        return runId;
    }

    public List<Event> events() {
        return List.copyOf(events);
    }

    public List<Map<String, Object>> history() {
        return List.copyOf(history);
    }

    public Set<String> sessionAllows() {
        return sessionAllows;
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public Event emit(String type, Map<String, Object> payload) {
        Event event = new Event(
                ProtocolConstants.PROTOCOL_VERSION,
                threadId,
                runId,
                seq.getAndIncrement(),
                type,
                Instant.now().toString(),
                payload == null ? Map.of() : new LinkedHashMap<>(payload));
        events.add(event);
        if (listener != null) {
            listener.accept(event);
        }
        return event;
    }

    public void appendHistory(Map<String, Object> item) {
        history.add(Map.copyOf(item));
    }

    public void replaceHistory(List<Map<String, Object>> messages) {
        history.clear();
        if (messages != null) {
            messages.forEach(m -> history.add(Map.copyOf(m)));
        }
    }

    public void recordModelUsage(com.anvil.core.model.ModelUsage usage) {
        if (usage == null) {
            return;
        }
        totalInputTokens += usage.inputTokens();
        totalOutputTokens += usage.outputTokens();
    }

    public void incrementToolCalls(int count) {
        toolCallCount += Math.max(0, count);
    }

    public Map<String, Object> usageSummary() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("input_tokens", totalInputTokens);
        map.put("output_tokens", totalOutputTokens);
        map.put("total_tokens", totalInputTokens + totalOutputTokens);
        map.put("tool_calls", toolCallCount);
        return map;
    }

    public RunAnchors anchors() {
        return anchors;
    }

    public boolean isVerifyFixRequired() {
        return verifyFixRequired;
    }

    public void setVerifyFixRequired(boolean required) {
        verifyFixRequired = required;
    }

    public void clearVerifyFixRequired() {
        verifyFixRequired = false;
        verifyFixTextOnlyRetries = 0;
    }

    public void recordWriteToolOutcome(boolean ok) {
        if (ok) {
            consecutiveWriteToolFailures = 0;
            return;
        }
        consecutiveWriteToolFailures++;
        if (consecutiveWriteToolFailures >= MAX_CONSECUTIVE_WRITE_FAILURES) {
            abortRun(ErrorCodes.TOOL_FAILED, "same write tool failed " + consecutiveWriteToolFailures + " times in a row");
        }
    }

    public int recordVerifyFixTextOnlyRetry() {
        verifyFixTextOnlyRetries++;
        if (verifyFixTextOnlyRetries >= MAX_VERIFY_FIX_TEXT_RETRIES) {
            abortRun(
                    ErrorCodes.TOOL_FAILED,
                    "verify/diagnostics still failing after " + verifyFixTextOnlyRetries + " text-only replies");
        }
        return verifyFixTextOnlyRetries;
    }

    public boolean shouldAbortRun() {
        return abortRun;
    }

    public RunStatus abortStatus() {
        return abortStatus;
    }

    public String abortMessage() {
        return abortMessage;
    }

    private String abortMessage = "";

    private void abortRun(String code, String message) {
        abortRun = true;
        abortStatus = RunStatus.FAILED;
        abortMessage = message;
        abortCode = code;
    }

    private String abortCode = ErrorCodes.TOOL_FAILED;

    public String abortCode() {
        return abortCode;
    }

    private volatile boolean plannerPhaseComplete;

    public boolean plannerPhaseComplete() {
        return plannerPhaseComplete;
    }

    public void markPlannerPhaseComplete() {
        plannerPhaseComplete = true;
    }
}
