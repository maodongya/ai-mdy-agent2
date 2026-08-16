package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ApprovalDecision {
    ALLOW_ONCE("allow_once"),
    ALLOW_SESSION("allow_session"),
    DENY("deny"),
    ALWAYS_DENY("always_deny");

    private final String wireValue;

    ApprovalDecision(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static ApprovalDecision fromWire(String value) {
        for (ApprovalDecision decision : values()) {
            if (decision.wireValue.equals(value)) {
                return decision;
            }
        }
        throw new IllegalArgumentException("unknown approval decision: " + value);
    }
}
