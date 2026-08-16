package com.anvil.cli;

import com.anvil.cli.commands.ApprovalCommand;
import com.anvil.cli.commands.RunCommand;
import com.anvil.cli.commands.ServeCommand;
import com.anvil.cli.commands.ThreadCommand;
import com.anvil.protocol.ProtocolConstants;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * Anvil 命令行入口：基于 picocli 定义顶层命令及子命令树。
 *
 * <p>顶层命令 {@code anvil} 提供以下子命令：
 * <ul>
 *   <li>{@code serve} —— 以 HTTP 和/或 JSON-RPC stdio 启动服务器；</li>
 *   <li>{@code thread} —— 管理会话线程；</li>
 *   <li>{@code run} —— 启动/订阅运行实例；</li>
 *   <li>{@code approval} —— 审批请求响应。</li>
 * </ul>
 * 未指定子命令时打印协议版本（{@code --protocol-version}）或顶层帮助。</p>
 */
@Command(
        name = "anvil",
        mixinStandardHelpOptions = true,
        version = "0.1.0-SNAPSHOT",
        description = "Anvil coding agent CLI",
        subcommands = {
            ServeCommand.class,
            ThreadCommand.class,
            RunCommand.class,
            ApprovalCommand.class,
            CommandLine.HelpCommand.class
        })
public class AnvilCli implements Runnable {

    /** 全局服务器选项（地址、端口等），通过 Mixin 注入到各子命令。 */
    @Mixin
    public ServerOptions serverOptions;

    /** 若指定则仅打印协议版本号后退出。 */
    @Option(names = "--protocol-version", description = "Print protocol version and exit")
    private boolean printProtocol;

    /** 顶层 picocli 命令对象，用于打印帮助。 */
    private CommandLine commandLine;

    /**
     * 程序入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        AnvilCli cli = new AnvilCli();
        CommandLine cmd = new CommandLine(cli);
        cli.commandLine = cmd;
        int exit = cmd.execute(args);
        System.exit(exit);
    }

    /**
     * 顶层命令被直接调用（无子命令）时的默认行为：
     * 打印协议版本（若请求）或顶层使用帮助。
     */
    @Override
    public void run() {
        if (printProtocol) {
            System.out.println(ProtocolConstants.PROTOCOL_VERSION);
        } else if (commandLine != null) {
            commandLine.usage(System.out);
        }
    }

    /**
     * 子命令需要打印顶层帮助时调用（无子命令时也可用）。
     */
    public void printHelpIfRequested() {
        if (commandLine != null) {
            commandLine.usage(System.out);
        }
    }
}
