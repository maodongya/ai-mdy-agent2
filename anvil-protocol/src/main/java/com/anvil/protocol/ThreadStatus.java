package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ThreadStatus {
    ACTIVE("active"),
    ARCHIVED("archived");

    private final String wireValue;

    ThreadStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static ThreadStatus fromWire(String value) {
        for (ThreadStatus status : values()) {
            if (status.wireValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown thread status: " + value);
    }
}
