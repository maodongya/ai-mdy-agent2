package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 审批决策枚举：Agent 请求高风险操作时，由用户做出的四种审批决定。
 */
public enum ApprovalDecision {
    /** 仅允许本次（一次性的同意）。 */
    ALLOW_ONCE("allow_once"),
    /** 本轮会话内始终允许。 */
    ALLOW_SESSION("allow_session"),
    /** 拒绝本次。 */
    DENY("deny"),
    /** 拒绝并记住该决策（本轮会话内不再询问）。 */
    ALWAYS_DENY("always_deny");

    /** 线协议（wire format）上的字符串表示。 */
    private final String wireValue;

    ApprovalDecision(String wireValue) {
        this.wireValue = wireValue;
    }

    /** 返回线协议字符串。 */
    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /**
     * 从线协议字符串解析枚举。
     *
     * @param value 线协议字符串
     * @return 对应的审批决策
     * @throws IllegalArgumentException 未知值时抛出
     */
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
