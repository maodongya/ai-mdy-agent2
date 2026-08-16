package com.anvil.tools;

import com.anvil.protocol.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrepToolTest {

    @TempDir
    Path workspace;

    @Test
    void findsMatchingLine() throws Exception {
        Files.writeString(workspace.resolve("Foo.java"), "class Foo {\n  int bar;\n}\n");
        ToolResult r = GrepTool.grep(workspace, "g1", "bar", null, false, 10);
        assertEquals("ok", r.status());
        assertTrue(r.content().contains("Foo.java:2:"));
    }
}
