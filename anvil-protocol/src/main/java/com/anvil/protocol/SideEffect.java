package com.anvil.protocol;

/**
 * 副作用类型枚举：描述工具操作可能产生的外部影响类别，
 * 供策略引擎与审批流程评估操作风险等级。
 */
public enum SideEffect {
    /** 只读操作（无副作用）。 */
    READ("read"),
    /** 写入工作区。 */
    WRITE_WORKSPACE("write_workspace"),
    /** 执行外部命令。 */
    EXEC("exec"),
    /** 网络访问。 */
    NETWORK("network"),
    /** 其他外部副作用。 */
    EXTERNAL_SIDE_EFFECT("external_side_effect");

    /** 线协议（wire format）上的字符串表示。 */
    private final String wireValue;

    SideEffect(String wireValue) {
        this.wireValue = wireValue;
    }

    /** 返回线协议字符串。 */
    public String wireValue() {
        return wireValue;
    }
}
