package com.anvil.cli.commands;

import com.anvil.cli.AnvilCli;
import com.anvil.cli.client.AnvilClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

/**
 * 审批命令组：管理待处理的 Agent 审批请求。
 *
 * <p>子命令 {@code approval respond} 允许 CLI 用户对某个待审批请求
 * （如 fs.write、shell.exec 等高风险操作）做出同意/拒绝等决策并反馈给服务器。</p>
 */
@Command(name = "approval", description = "Approval operations", subcommands = ApprovalRespondCommand.class)
public class ApprovalCommand implements Callable<Integer> {

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
 * 审批响应子命令：对指定审批请求做出决策。
 */
@Command(name = "respond", description = "Respond to a pending approval")
class ApprovalRespondCommand implements Callable<Integer> {

    /** 所属的审批命令组。 */
    @ParentCommand
    private ApprovalCommand approvalCommand;

    /** 待响应的审批请求标识。 */
    @Option(names = "--id", required = true, description = "Approval id")
    String id;

    /** 审批决策：allow_once | allow_session | deny | always_deny。 */
    @Option(
            names = "--decision",
            required = true,
            description = "allow_once|allow_session|deny|always_deny")
    String decision;

    /**
     * 执行审批响应。
     *
     * @return 退出码 0
     */
    @Override
    public Integer call() throws Exception {
        AnvilClient client = new AnvilClient(approvalCommand.serverUrl());
        client.respondApproval(id, decision);
        System.out.println(id + " " + decision);
        return 0;
    }
}
