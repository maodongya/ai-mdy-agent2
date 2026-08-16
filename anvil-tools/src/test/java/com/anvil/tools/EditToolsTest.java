package com.anvil.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditToolsTest {

    @TempDir
    Path workspace;

    private FsTools fs;

    @BeforeEach
    void setUp() throws Exception {
        Path file = workspace.resolve("Hello.java");
        Files.writeString(file, "public class Hello {\n  void a() {}\n}\n");
        fs = new FsTools(workspace);
    }

    @Test
    void searchReplaceSingleMatch() throws Exception {
        var r = EditTools.searchReplace(fs, "c1", "Hello.java", "void a()", "void b()", false);
        assertEquals("ok", r.status());
        assertTrue(Files.readString(workspace.resolve("Hello.java")).contains("void b()"));
    }

    @Test
    void searchReplaceFailsOnMultipleWithoutReplaceAll() throws Exception {
        Path file = workspace.resolve("dup.txt");
        Files.writeString(file, "x\nx\n");
        fs = new FsTools(workspace);
        var r = EditTools.searchReplace(fs, "c2", "dup.txt", "x", "y", false);
        assertEquals("error", r.status());
    }

    @Test
    void searchReplaceFuzzyWhitespace() throws Exception {
        Files.writeString(workspace.resolve("Spaced.java"), "void  a()  {}\n");
        var r = EditTools.searchReplace(fs, "c3", "Spaced.java", "void a()", "void b()", false);
        assertEquals("ok", r.status());
        assertTrue(Files.readString(workspace.resolve("Spaced.java")).contains("void b()"));
    }

    @Test
    void applyMultiFilePatchViaTool(@TempDir Path workspace) throws Exception {
        Files.writeString(workspace.resolve("X.java"), "x\n");
        Files.writeString(workspace.resolve("Y.java"), "y\n");
        FsTools fsTools = new FsTools(workspace);
        String patch =
                """
                --- a/X.java
                +++ b/X.java
                @@ -1,1 +1,1 @@
                -x
                +X
                --- a/Y.java
                +++ b/Y.java
                @@ -1,1 +1,1 @@
                -y
                +Y
                """;
        var r = EditTools.applyMultiFilePatch(fsTools, "mf", patch);
        assertEquals("ok", r.status());
        assertEquals("X\n", Files.readString(workspace.resolve("X.java")));
        assertEquals("Y\n", Files.readString(workspace.resolve("Y.java")));
    }

    @Test
    void applyUnifiedPatch() {
        String original = "aaa\nbbb\nccc\n";
        String patch = "@@ -2,1 +2,1 @@\n-bbb\n+BBB\n";
        String updated = EditTools.applyUnifiedPatch(original, patch);
        assertEquals("aaa\nBBB\nccc\n", updated);
    }
}
