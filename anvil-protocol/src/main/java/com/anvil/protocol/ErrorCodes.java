package com.anvil.protocol;

/**
 * 稳定错误码集合 —— 参见 智能体a/02-产品规格/02-核心API与协议.md §11。
 *
 * <p>这些错误码在协议中保持稳定，用于统一识别各类失败场景：
 * 模型不可用/超时、工具参数非法/超时/失败、策略拒绝、审批拒绝/超时、
 * 预算超限、上下文耗尽、工作区冲突、取消与内部错误。</p>
 */
public final class ErrorCodes {

    /** 模型服务不可用。 */
    public static final String MODEL_UNAVAILABLE = "MODEL_UNAVAILABLE";
    /** 模型调用超时。 */
    public static final String MODEL_TIMEOUT = "MODEL_TIMEOUT";
    /** 模型返回了无法解析的响应。 */
    public static final String MODEL_BAD_RESPONSE = "MODEL_BAD_RESPONSE";
    /** 工具参数非法。 */
    public static final String TOOL_ARG_INVALID = "TOOL_ARG_INVALID";
    /** 工具执行超时。 */
    public static final String TOOL_TIMEOUT = "TOOL_TIMEOUT";
    /** 工具执行失败。 */
    public static final String TOOL_FAILED = "TOOL_FAILED";
    /** 操作被策略引擎拒绝。 */
    public static final String POLICY_DENIED = "POLICY_DENIED";
    /** 操作被审批流程拒绝。 */
    public static final String APPROVAL_DENIED = "APPROVAL_DENIED";
    /** 审批请求超时未决策。 */
    public static final String APPROVAL_TIMEOUT = "APPROVAL_TIMEOUT";
    /** 超过预算上限。 */
    public static final String BUDGET_EXCEEDED = "BUDGET_EXCEEDED";
    /** 上下文（token）耗尽。 */
    public static final String CONTEXT_EXHAUSTED = "CONTEXT_EXHAUSTED";
    /** 工作区资源冲突。 */
    public static final String WORKSPACE_CONFLICT = "WORKSPACE_CONFLICT";
    /** 运行被取消。 */
    public static final String CANCELLED = "CANCELLED";
    /** 服务器内部未知错误。 */
    public static final String INTERNAL = "INTERNAL";

    private ErrorCodes() {}
}
