package com.anvil.core.policy;

import com.anvil.protocol.Mode;
import com.anvil.protocol.SideEffect;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PolicyEngineTest {

    @Test
    void askDeniesWrite() {
        Decision d = PolicyEngine.evaluate(new PolicyInput(
                Mode.ASK,
                "fs.write",
                SideEffect.WRITE_WORKSPACE,
                Map.of("summary", "write", "paths", List.of("a.java")),
                Set.of()));
        assertEquals(Decision.Type.DENY, d.type());
    }

    @Test
    void agentApprovesWrite() {
        Decision d = PolicyEngine.evaluate(new PolicyInput(
                Mode.AGENT,
                "fs.write",
                SideEffect.WRITE_WORKSPACE,
                Map.of("summary", "write", "paths", List.of("a.java")),
                Set.of()));
        assertEquals(Decision.Type.APPROVE, d.type());
    }

    @Test
    void readAlwaysAllowed() {
        Decision d = PolicyEngine.evaluate(new PolicyInput(
                Mode.ASK, "fs.read", SideEffect.READ, Map.of("summary", "read"), Set.of()));
        assertEquals(Decision.Type.ALLOW, d.type());
    }

    @Test
    void planAllowsPlanFileOnly() {
        Decision d = PolicyEngine.evaluate(new PolicyInput(
                Mode.PLAN,
                "fs.write",
                SideEffect.WRITE_WORKSPACE,
                Map.of("summary", "plan", "paths", List.of(".anvil/plan.md")),
                Set.of()));
        assertEquals(Decision.Type.APPROVE, d.type());
    }

    @Test
    void sessionAllowBypassesApproval() {
        Decision d = PolicyEngine.evaluate(new PolicyInput(
                Mode.AGENT,
                "fs.write",
                SideEffect.WRITE_WORKSPACE,
                Map.of("paths", List.of("a.java")),
                Set.of("fs.write")));
        assertEquals(Decision.Type.ALLOW, d.type());
    }

    @Test
    void agentAutoAllowsSearchReplace() {
        Decision d = PolicyEngine.evaluate(new PolicyInput(
                Mode.AGENT,
                "search_replace",
                SideEffect.WRITE_WORKSPACE,
                Map.of("paths", List.of("a.java")),
                Set.of(),
                true));
        assertEquals(Decision.Type.ALLOW, d.type());
    }

    @Test
    void askStillDeniesSearchReplace() {
        Decision d = PolicyEngine.evaluate(new PolicyInput(
                Mode.ASK,
                "search_replace",
                SideEffect.WRITE_WORKSPACE,
                Map.of("paths", List.of("a.java")),
                Set.of(),
                true));
        assertEquals(Decision.Type.DENY, d.type());
    }

    @Test
    void agentAutoAllowsPlanUpdate() {
        Decision d = PolicyEngine.evaluate(new PolicyInput(
                Mode.AGENT,
                "plan.update",
                SideEffect.WRITE_WORKSPACE,
                Map.of("paths", List.of(".anvil/plan.md"), "summary", "update plan"),
                Set.of(),
                true));
        assertEquals(Decision.Type.ALLOW, d.type());
    }

    @Test
    void yoloAutoAllowsFsWrite() {
        Decision d = PolicyEngine.evaluate(new PolicyInput(
                Mode.AGENT,
                "fs.write",
                SideEffect.WRITE_WORKSPACE,
                Map.of("paths", List.of("a.java")),
                Set.of(),
                true,
                true));
        assertEquals(Decision.Type.ALLOW, d.type());
    }

    @Test
    void yoloDoesNotAllowShell() {
        Decision d = PolicyEngine.evaluate(new PolicyInput(
                Mode.AGENT,
                "shell.exec",
                SideEffect.EXEC,
                Map.of("command", "rm -rf /"),
                Set.of(),
                true,
                true));
        assertEquals(Decision.Type.APPROVE, d.type());
    }
}
