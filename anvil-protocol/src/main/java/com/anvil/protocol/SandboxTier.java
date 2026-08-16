package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 沙箱隔离层级枚举：定义 Agent 工具执行时的工作区权限边界。
 */
public enum SandboxTier {
    /** 仅可写工作区（默认，路径守卫限制在工作区内）。 */
    WORKSPACE_WRITE("workspace_write"),
    /** 只读模式：禁止任何写操作。 */
    READ_ONLY("read_only"),
    /** 云端隔离：在独立沙箱容器中执行。 */
    CLOUD_ISOLATED("cloud_isolated"),
    /** 加固模式：最强的安全约束。 */
    HARDENED("hardened");

    /** 线协议（wire format）上的字符串表示。 */
    private final String wireValue;

    SandboxTier(String wireValue) {
        this.wireValue = wireValue;
    }

    /** 返回线协议字符串。 */
    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /**
     * 从线协议字符串解析层级。
     *
     * @param value 线协议字符串
     * @return 对应的沙箱层级
     * @throws IllegalArgumentException 未知值时抛出
     */
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
