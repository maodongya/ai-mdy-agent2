package com.anvil.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditPlanToolTest {

    @Test
    void appliesMultipleOperations(@TempDir Path workspace) throws Exception {
        Files.writeString(workspace.resolve("A.java"), "void a() {}\n");
        Files.writeString(workspace.resolve("B.java"), "void b() {}\n");
        FsTools fs = new FsTools(workspace);
        String ops =
                """
                [
                  {"path":"A.java","old_string":"void a()","new_string":"void aa()"},
                  {"path":"B.java","old_string":"void b()","new_string":"void bb()"}
                ]
                """;
        var r = EditPlanTool.execute(fs, "p1", ops);
        assertEquals("ok", r.status());
        assertTrue(Files.readString(workspace.resolve("A.java")).contains("void aa()"));
        assertTrue(Files.readString(workspace.resolve("B.java")).contains("void bb()"));
    }
}
