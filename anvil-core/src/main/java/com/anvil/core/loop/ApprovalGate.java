package com.anvil.core.loop;

import com.anvil.protocol.ApprovalDecision;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface ApprovalGate {

    CompletableFuture<ApprovalDecision> waitForApproval(
            String approvalId, Map<String, Object> preview, long timeoutMs);
}
