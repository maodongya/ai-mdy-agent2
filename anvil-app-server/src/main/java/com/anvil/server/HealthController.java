package com.anvil.server;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.anvil.protocol.ProtocolConstants;
import com.anvil.server.api.WorkspaceScanner;
import com.anvil.server.config.AnvilContextConfig;

@RestController
public class HealthController {

    private final AnvilContextConfig contextConfig;

    public HealthController(AnvilContextConfig contextConfig) {
        this.contextConfig = contextConfig;
    }

    @GetMapping("/api/health")
    public HealthResponse health() {
        var budget = contextConfig.baseBudget();
        return new HealthResponse(
                true,
                "anvil",
                ProtocolConstants.PROTOCOL_VERSION,
                WorkspaceScanner.MAX_DEPTH,
                budget.compactThresholdTokens(),
                budget.keepRecentMessages(),
                contextConfig.maxStepsDefault());
    }

    public record HealthResponse(
            boolean ok,
            String name,
            String protocolVersion,
            int workspaceScanMaxDepth,
            int contextCompactThreshold,
            int contextKeepRecent,
            int maxStepsDefault) {}
}
