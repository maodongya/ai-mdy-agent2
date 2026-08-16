package com.anvil.server.service;

import com.anvil.tools.index.IndexService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 后台工作区索引构建服务（Phase 3）。 */
@Service
public final class WorkspaceIndexService {

    /** 虚拟线程执行器，用于异步构建工作区索引。 */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    /** 是否启用自动预热（auto-warm）。 */
    private final boolean autoWarm;

    /**
     * 构造函数。
     *
     * @param autoWarm 是否在启动运行时自动预热工作区索引
     */
    public WorkspaceIndexService(@Value("${anvil.index.auto-warm:true}") boolean autoWarm) {
        this.autoWarm = autoWarm;
    }

    /**
     * 异步预热指定工作区的索引。
     *
     * <p>若禁用自动预热或工作区路径为空则直接跳过；
     * 否则在后台虚拟线程中构建索引，不阻塞调用方。
     *
     * @param workspaceRoot 工作区根目录
     */
    public void warmAsync(Path workspaceRoot) {
        if (!autoWarm || workspaceRoot == null) {
            return;
        }
        executor.submit(() -> IndexService.warm(workspaceRoot));
    }
}
