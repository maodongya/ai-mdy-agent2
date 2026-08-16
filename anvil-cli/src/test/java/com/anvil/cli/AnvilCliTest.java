package com.anvil.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnvilCliTest {

    @Test
    void printsProtocolVersion() {
        int exit = new CommandLine(new AnvilCli()).execute("--protocol-version");
        assertEquals(0, exit);
    }
}
