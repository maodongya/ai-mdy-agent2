package com.anvil.core.policy;

import com.anvil.protocol.Mode;
import com.anvil.protocol.SideEffect;

import java.util.Map;
import java.util.Set;

public record PolicyInput(
        Mode mode,
        String toolName,
        SideEffect sideEffect,
        Map<String, Object> preview,
        Set<String> sessionAllows,
        boolean autoApprovePatchTools,
        boolean autoApproveWrites) {

    public PolicyInput(Mode mode, String toolName, SideEffect sideEffect, Map<String, Object> preview, Set<String> sessionAllows) {
        this(mode, toolName, sideEffect, preview, sessionAllows, true, false);
    }

    public PolicyInput(
            Mode mode,
            String toolName,
            SideEffect sideEffect,
            Map<String, Object> preview,
            Set<String> sessionAllows,
            boolean autoApprovePatchTools) {
        this(mode, toolName, sideEffect, preview, sessionAllows, autoApprovePatchTools, false);
    }
}
