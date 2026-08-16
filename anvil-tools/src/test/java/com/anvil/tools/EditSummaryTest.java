package com.anvil.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditSummaryTest {

    @Test
    void countsReplaceLines() {
        EditSummary.Delta delta = EditSummary.forReplace("Foo.java", "a\nb\n", "x\n", 1);

        assertEquals("Foo.java", delta.path());
        assertEquals(1, delta.linesAdded());
        assertEquals(2, delta.linesRemoved());
    }

    @Test
    void countsPatchHunkLines() {
        String patch =
                """
                --- a/Foo.java
                +++ b/Foo.java
                @@ -1,2 +1,3 @@
                 line1
                +added
                 line2
                """;

        EditSummary.Delta delta = EditSummary.forPatch("Foo.java", patch);

        assertEquals(1, delta.linesAdded());
        assertEquals(0, delta.linesRemoved());
    }

    @Test
    void infersModuleScopedVerifyCommand(@TempDir Path workspace) throws Exception {
        Path module = workspace.resolve("anvil-core");
        Files.createDirectories(module);
        Files.writeString(workspace.resolve("pom.xml"), "<project/>");
        Files.writeString(module.resolve("pom.xml"), "<project/>");

        String command = EditSummary.inferVerifyCommand(workspace, "anvil-core/src/Foo.java", "");

        assertEquals("mvn -q test -pl anvil-core -am", command);
    }

    @Test
    void usesTemplateWhenProvided(@TempDir Path workspace) throws Exception {
        Files.createDirectories(workspace.resolve("anvil-core"));
        Files.writeString(workspace.resolve("pom.xml"), "<project/>");

        String command =
                EditSummary.inferVerifyCommand(workspace, "anvil-core/src/Foo.java", "mvn -q test -pl {module} -am");

        assertEquals("mvn -q test -pl anvil-core -am", command);
    }

    @Test
    void returnsNullWhenNotMaven(@TempDir Path workspace) {
        assertNull(EditSummary.inferVerifyCommand(workspace, "src/Foo.java", ""));
    }
}
