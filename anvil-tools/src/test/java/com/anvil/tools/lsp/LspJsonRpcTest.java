package com.anvil.tools.lsp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LspJsonRpcTest {

    @TempDir
    Path workspace;

    @Test
    void uriToRelativePath() {
        Path file = workspace.resolve("src/Foo.java").toAbsolutePath().normalize();
        String rel = LspJsonRpc.uriToRelative(file.toUri().toString(), workspace);
        assertEquals("src/Foo.java", rel);
    }
}
