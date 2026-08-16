package com.anvil.server.service;

import com.anvil.core.loop.ApprovalGate;
import com.anvil.protocol.ApprovalDecision;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ApprovalService implements ApprovalGate {

    private static final long EARLY_RESPONSE_TTL_MS = 30 * 60 * 1000L;

    private final Map<String, CompletableFuture<ApprovalDecision>> pending = new ConcurrentHashMap<>();
    /** UI may respond before the loop thread registers the future; buffer until waitForApproval. */
    private final Map<String, TimedDecision> earlyResponses = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<ApprovalDecision> waitForApproval(
            String approvalId, Map<String, Object> preview, long timeoutMs) {
        purgeExpiredEarlyResponses();
        TimedDecision early = earlyResponses.remove(approvalId);
        if (early != null) {
            return CompletableFuture.completedFuture(early.decision());
        }
        return pending.computeIfAbsent(approvalId, id -> new CompletableFuture<>());
    }

    public boolean respond(String approvalId, ApprovalDecision decision) {
        purgeExpiredEarlyResponses();
        CompletableFuture<ApprovalDecision> future = pending.remove(approvalId);
        if (future != null) {
            future.complete(decision);
            return true;
        }
        earlyResponses.put(approvalId, new TimedDecision(decision, System.currentTimeMillis()));
        return true;
    }

    private void purgeExpiredEarlyResponses() {
        long cutoff = System.currentTimeMillis() - EARLY_RESPONSE_TTL_MS;
        earlyResponses.entrySet().removeIf(entry -> entry.getValue().createdAtMs() < cutoff);
    }

    /** Test hook: mark buffered responses as expired. */
    void testingForceExpireAllEarlyResponses() {
        long expired = System.currentTimeMillis() - EARLY_RESPONSE_TTL_MS - 1;
        earlyResponses.replaceAll((id, timed) -> new TimedDecision(timed.decision(), expired));
        purgeExpiredEarlyResponses();
    }

    private record TimedDecision(ApprovalDecision decision, long createdAtMs) {}
}
