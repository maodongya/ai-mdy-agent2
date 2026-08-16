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
    void applyUnifiedPatch() {
        String original = "aaa\nbbb\nccc\n";
        String patch = "@@ -2,1 +2,1 @@\n-bbb\n+BBB\n";
        String updated = EditTools.applyUnifiedPatch(original, patch);
        assertEquals("aaa\nBBB\nccc\n", updated);
    }
}
