package com.anvil.server.service;

import com.anvil.core.loop.ApprovalGate;
import com.anvil.protocol.ApprovalDecision;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ApprovalService implements ApprovalGate {

    private final Map<String, CompletableFuture<ApprovalDecision>> pending = new ConcurrentHashMap<>();
    /** UI may respond before the loop thread registers the future; buffer until waitForApproval. */
    private final Map<String, ApprovalDecision> earlyResponses = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<ApprovalDecision> waitForApproval(
            String approvalId, Map<String, Object> preview, long timeoutMs) {
        ApprovalDecision early = earlyResponses.remove(approvalId);
        if (early != null) {
            return CompletableFuture.completedFuture(early);
        }
        return pending.computeIfAbsent(approvalId, id -> new CompletableFuture<>());
    }

    public boolean respond(String approvalId, ApprovalDecision decision) {
        CompletableFuture<ApprovalDecision> future = pending.remove(approvalId);
        if (future != null) {
            future.complete(decision);
            return true;
        }
        earlyResponses.put(approvalId, decision);
        return true;
    }
}
