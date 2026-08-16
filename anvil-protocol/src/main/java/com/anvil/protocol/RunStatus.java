package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 运行状态枚举：描述一次 Agent 运行实例的生命周期状态。
 */
public enum RunStatus {
    /** 已排队，等待执行。 */
    QUEUED("queued"),
    /** 正在运行中。 */
    RUNNING("running"),
    /** 等待用户审批。 */
    WAITING_APPROVAL("waiting_approval"),
    /** 运行成功完成。 */
    SUCCEEDED("succeeded"),
    /** 运行失败。 */
    FAILED("failed"),
    /** 运行被取消。 */
    CANCELLED("cancelled");

    /** 线协议（wire format）上的字符串表示。 */
    private final String wireValue;

    RunStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    /** 返回线协议字符串。 */
    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /**
     * 从线协议字符串解析状态。
     *
     * @param value 线协议字符串
     * @return 对应的运行状态
     * @throws IllegalArgumentException 未知值时抛出
     */
    @JsonCreator
    public static RunStatus fromWire(String value) {
        for (RunStatus status : values()) {
            if (status.wireValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown run status: " + value);
    }
}
