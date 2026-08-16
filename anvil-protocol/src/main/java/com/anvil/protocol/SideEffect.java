package com.anvil.protocol;

public enum SideEffect {
    READ("read"),
    WRITE_WORKSPACE("write_workspace"),
    EXEC("exec"),
    NETWORK("network"),
    EXTERNAL_SIDE_EFFECT("external_side_effect");

    private final String wireValue;

    SideEffect(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
