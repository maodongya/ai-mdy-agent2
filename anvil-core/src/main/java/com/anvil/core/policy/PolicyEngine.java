package com.anvil.core.policy;

import com.anvil.protocol.ErrorCodes;
import com.anvil.protocol.Mode;
import com.anvil.protocol.SideEffect;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PolicyEngine {

    private static final String PLAN_FILE = ".anvil/plan.md";

    private PolicyEngine() {}

    public static Decision evaluate(PolicyInput input) {
        String sessionKey = input.toolName() + ":" + input.sideEffect().wireValue();
        if (input.sessionAllows().contains(input.toolName()) || input.sessionAllows().contains(sessionKey)) {
            return Decision.allow();
        }

        if (input.sideEffect() == SideEffect.READ) {
            return Decision.allow();
        }

        if (input.autoApprovePatchTools() && isAutoAllowedLowRiskWrite(input.mode(), input.toolName(), input.preview())) {
            return Decision.allow();
        }

        if (input.autoApproveWrites()
                && input.sideEffect() == SideEffect.WRITE_WORKSPACE
                && isYoloWriteTool(input.mode(), input.toolName())) {
            return Decision.allow();
        }

        if (input.mode() == Mode.ASK) {
            return Decision.deny(ErrorCodes.POLICY_DENIED, "mode ask cannot use " + input.sideEffect().wireValue());
        }

        if (input.mode() == Mode.PLAN) {
            if (input.sideEffect() == SideEffect.WRITE_WORKSPACE && isPlanOnlyWrite(input.preview())) {
                return Decision.approve(input.preview());
            }
            return Decision.deny(ErrorCodes.POLICY_DENIED, "mode plan cannot use " + input.sideEffect().wireValue());
        }

        if (input.sideEffect() == SideEffect.WRITE_WORKSPACE
                || input.sideEffect() == SideEffect.EXEC
                || input.sideEffect() == SideEffect.NETWORK
                || input.sideEffect() == SideEffect.EXTERNAL_SIDE_EFFECT) {
            return Decision.approve(input.preview());
        }

        return Decision.allow();
    }

    private static boolean isAutoAllowedLowRiskWrite(Mode mode, String toolName, Map<String, Object> preview) {
        if (mode != Mode.AGENT && mode != Mode.DEBUG) {
            return false;
        }
        if ("search_replace".equals(toolName) || "apply_patch".equals(toolName)) {
            return true;
        }
        return "plan.update".equals(toolName) && isPlanOnlyWrite(preview);
    }

    private static boolean isYoloWriteTool(Mode mode, String toolName) {
        if (mode != Mode.AGENT && mode != Mode.DEBUG) {
            return false;
        }
        return "fs.write".equals(toolName);
    }

    @SuppressWarnings("unchecked")
    private static boolean isPlanOnlyWrite(Map<String, Object> preview) {
        Object pathsObj = preview.get("paths");
        if (!(pathsObj instanceof List<?> paths)) {
            return false;
        }
        return paths.size() == 1 && PLAN_FILE.equals(String.valueOf(paths.get(0)));
    }
}
