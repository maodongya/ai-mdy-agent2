package com.anvil.protocol;

/** Stable error codes — see 智能体a/02-产品规格/02-核心API与协议.md §11 */
public final class ErrorCodes {

    public static final String MODEL_UNAVAILABLE = "MODEL_UNAVAILABLE";
    public static final String MODEL_TIMEOUT = "MODEL_TIMEOUT";
    public static final String MODEL_BAD_RESPONSE = "MODEL_BAD_RESPONSE";
    public static final String TOOL_ARG_INVALID = "TOOL_ARG_INVALID";
    public static final String TOOL_TIMEOUT = "TOOL_TIMEOUT";
    public static final String TOOL_FAILED = "TOOL_FAILED";
    public static final String POLICY_DENIED = "POLICY_DENIED";
    public static final String APPROVAL_DENIED = "APPROVAL_DENIED";
    public static final String APPROVAL_TIMEOUT = "APPROVAL_TIMEOUT";
    public static final String BUDGET_EXCEEDED = "BUDGET_EXCEEDED";
    public static final String CONTEXT_EXHAUSTED = "CONTEXT_EXHAUSTED";
    public static final String WORKSPACE_CONFLICT = "WORKSPACE_CONFLICT";
    public static final String CANCELLED = "CANCELLED";
    public static final String INTERNAL = "INTERNAL";

    private ErrorCodes() {}
}
