package com.anvil.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 线程状态枚举：描述会话线程的生命周期状态。
 */
public enum ThreadStatus {
    /** 激活：可正常运行。 */
    ACTIVE("active"),
    /** 已归档：已关闭，不再承载运行。 */
    ARCHIVED("archived");

    /** 线协议（wire format）上的字符串表示。 */
    private final String wireValue;

    ThreadStatus(String wireValue) {
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
     * @return 对应的线程状态
     * @throws IllegalArgumentException 未知值时抛出
     */
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
