package com.anvil.cli;

import picocli.CommandLine;
import picocli.CommandLine.Option;

public class ServerOptions {

    @Option(
            names = {"--server"},
            defaultValue = "http://127.0.0.1:7788",
            description = "App Server base URL",
            scope = CommandLine.ScopeType.INHERIT)
    public String server;
}
