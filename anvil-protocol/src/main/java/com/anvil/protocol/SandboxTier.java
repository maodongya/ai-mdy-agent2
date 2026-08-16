package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SandboxTier {
    WORKSPACE_WRITE("workspace_write"),
    READ_ONLY("read_only"),
    CLOUD_ISOLATED("cloud_isolated"),
    HARDENED("hardened");

    private final String wireValue;

    SandboxTier(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static SandboxTier fromWire(String value) {
        for (SandboxTier tier : values()) {
            if (tier.wireValue.equals(value)) {
                return tier;
            }
        }
        throw new IllegalArgumentException("unknown sandbox tier: " + value);
    }
}
