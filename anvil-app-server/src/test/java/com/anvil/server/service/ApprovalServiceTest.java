package com.anvil.server.service;

import com.anvil.protocol.ApprovalDecision;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalServiceTest {

    @Test
    void buffersEarlyResponseUntilWaitForApproval() throws Exception {
        ApprovalService service = new ApprovalService();
        assertTrue(service.respond("appr_test", ApprovalDecision.ALLOW_ONCE));

        ApprovalDecision decision = service
                .waitForApproval("appr_test", Map.of(), 30_000)
                .get(1, TimeUnit.SECONDS);

        assertEquals(ApprovalDecision.ALLOW_ONCE, decision);
    }

    @Test
    void completesPendingFutureWhenResponseArrivesAfterWait() throws Exception {
        ApprovalService service = new ApprovalService();
        var future = service.waitForApproval("appr_late", Map.of(), 30_000);
        assertTrue(service.respond("appr_late", ApprovalDecision.DENY));
        assertEquals(ApprovalDecision.DENY, future.get(1, TimeUnit.SECONDS));
    }

    @Test
    void expiresStaleEarlyResponses() throws Exception {
        ApprovalService service = new ApprovalService();
        assertTrue(service.respond("appr_stale", ApprovalDecision.ALLOW_ONCE));
        service.testingForceExpireAllEarlyResponses();
        var future = service.waitForApproval("appr_stale", Map.of(), 30_000);
        assertFalse(future.isDone());
    }
}
