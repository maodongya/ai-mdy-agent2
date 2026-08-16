package com.anvil.core.policy;

import java.util.Map;

public record Decision(Type type, String code, String message, Map<String, Object> preview) {

    public enum Type {
        ALLOW,
        DENY,
        APPROVE
    }

    public static Decision allow() {
        return new Decision(Type.ALLOW, null, null, null);
    }

    public static Decision deny(String code, String message) {
        return new Decision(Type.DENY, code, message, null);
    }

    public static Decision approve(Map<String, Object> preview) {
        return new Decision(Type.APPROVE, null, null, preview);
    }
}
