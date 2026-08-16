package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Agent 权限模式（与模型选择正交）。
 *
 * <p>决定 Agent 在本次运行中被授予多大的自主行动权，
 * 从纯问答（ask）到可自主执行工具（agent）不等。</p>
 */
public enum Mode {
    /** 仅问答模式：不做任何高影响操作。 */
    ASK("ask"),
    /** 计划模式：产出计划，不执行工具。 */
    PLAN("plan"),
    /** 代理模式：可自主执行工具（受策略约束）。 */
    AGENT("agent"),
    /** 调试模式：逐步审查执行过程。 */
    DEBUG("debug");

    /** 线协议（wire format）上的字符串表示。 */
    private final String wireValue;

    Mode(String wireValue) {
        this.wireValue = wireValue;
    }

    /** 返回线协议字符串。 */
    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /**
     * 从线协议字符串解析模式。
     *
     * @param value 线协议字符串
     * @return 对应的模式
     * @throws IllegalArgumentException 未知值时抛出
     */
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
