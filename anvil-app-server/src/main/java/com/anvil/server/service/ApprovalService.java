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

    @Override
    public CompletableFuture<ApprovalDecision> waitForApproval(
            String approvalId, Map<String, Object> preview, long timeoutMs) {
        return pending.computeIfAbsent(approvalId, id -> new CompletableFuture<>());
    }

    public boolean respond(String approvalId, ApprovalDecision decision) {
        CompletableFuture<ApprovalDecision> future = pending.remove(approvalId);
        if (future == null) {
            return false;
        }
        future.complete(decision);
        return true;
    }
}
