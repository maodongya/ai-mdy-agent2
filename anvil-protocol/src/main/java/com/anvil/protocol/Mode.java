package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Agent permission mode (orthogonal to model selection). */
public enum Mode {
    ASK("ask"),
    PLAN("plan"),
    AGENT("agent"),
    DEBUG("debug");

    private final String wireValue;

    Mode(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static Mode fromWire(String value) {
        for (Mode mode : values()) {
            if (mode.wireValue.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("unknown mode: " + value);
    }
}
