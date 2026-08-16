package com.anvil.ui;

/** 与服务器健康检查共享的常量。 */
final class WorkspaceScanner {

    /**
     * 扫描代码预期支持的最小目录深度。
     *
     * <p>供 UI/健康检查使用，用于防止服务器无法遍历常见的源码目录结构
     * （例如 {@code src/main/java/...}）。该值与服务端 {@code MAX_DEPTH} 常量
     * 保持一致，以确保两侧对扫描必须可靠到达的深度达成一致。
     */
    static final int MIN_EXPECTED_DEPTH = 20;

    /** 工具类 —— 不允许实例化。 */
    private WorkspaceScanner() {}
}
