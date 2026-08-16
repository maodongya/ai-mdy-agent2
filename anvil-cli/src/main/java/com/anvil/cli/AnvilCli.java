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

    @Mixin
    public ServerOptions serverOptions;

    @Option(names = "--protocol-version", description = "Print protocol version and exit")
    private boolean printProtocol;

    private CommandLine commandLine;

    public static void main(String[] args) {
        AnvilCli cli = new AnvilCli();
        CommandLine cmd = new CommandLine(cli);
        cli.commandLine = cmd;
        int exit = cmd.execute(args);
        System.exit(exit);
    }

    @Override
    public void run() {
        if (printProtocol) {
            System.out.println(ProtocolConstants.PROTOCOL_VERSION);
        } else if (commandLine != null) {
            commandLine.usage(System.out);
        }
    }

    public void printHelpIfRequested() {
        if (commandLine != null) {
            commandLine.usage(System.out);
        }
    }
}
