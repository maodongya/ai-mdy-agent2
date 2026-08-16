package com.anvil.cli.commands;

import com.anvil.cli.AnvilCli;
import com.anvil.cli.client.AnvilClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

/**
 * 线程命令组：管理会话线程。
 *
 * <p>子命令：{@code thread create}（创建一个绑定工作区的线程）。
 * 每个线程对应一个独立工作区上下文，可承载多次运行。</p>
 */
@Command(name = "thread", description = "Thread operations", subcommands = ThreadCreateCommand.class)
public class ThreadCommand implements Callable<Integer> {

    /** 父命令行入口，用于获取服务器地址等全局选项。 */
    @ParentCommand
    private AnvilCli parent;

    /**
     * 获取服务器基础地址。
     *
     * @return 服务器 URL
     */
    String serverUrl() {
        return parent.serverOptions.server;
    }

    /**
     * 无参数调用时仅打印帮助信息。
     *
     * @return 退出码 0
     */
    @Override
    public Integer call() {
        parent.printHelpIfRequested();
        return 0;
    }
}

/**
 * 创建线程子命令：在服务器上创建一个绑定到工作目录的线程。
 */
@Command(name = "create", description = "Create a thread bound to a workspace directory")
class ThreadCreateCommand implements Callable<Integer> {

    /** 所属的线程命令组。 */
    @ParentCommand
    private ThreadCommand threadCommand;

    /** 工作区根目录（默认当前目录）。 */
    @picocli.CommandLine.Option(names = "--cwd", defaultValue = ".", description = "Workspace root")
    String cwd;

    /**
     * 执行线程创建并打印结果。
     *
     * @return 退出码 0
     */
    @Override
    public Integer call() throws Exception {
        AnvilClient client = new AnvilClient(threadCommand.serverUrl());
        var result = client.createThread(cwd);
        System.out.println(result.get("thread_id") + " " + result.get("workspace_root"));
        return 0;
    }
}
