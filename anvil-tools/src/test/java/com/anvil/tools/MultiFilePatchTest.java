package com.anvil.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiFilePatchTest {

    @Test
    void parsesMultipleFiles() {
        String patch =
                """
                --- a/A.java
                +++ b/A.java
                @@ -1,1 +1,1 @@
                -a
                +A
                --- a/B.java
                +++ b/B.java
                @@ -1,1 +1,1 @@
                -b
                +B
                """;
        List<MultiFilePatch.FilePatch> files = MultiFilePatch.parse(patch);
        assertEquals(2, files.size());
    }

    @Test
    void applyAllRollsBackOnFailure(@TempDir Path workspace) throws Exception {
        Files.writeString(workspace.resolve("ok.txt"), "keep\n");
        Files.writeString(workspace.resolve("bad.txt"), "old\n");
        List<MultiFilePatch.FilePatch> patches = List.of(
                new MultiFilePatch.FilePatch("ok.txt", "@@ -1,1 +1,1 @@\n-keep\n+KEEP\n"),
                new MultiFilePatch.FilePatch("bad.txt", "@@ -1,1 +1,1 @@\n-NOMATCH\n+NEW\n"));
        String err = MultiFilePatch.applyAll(workspace, patches);
        assertTrue(err != null);
        assertEquals("keep\n", Files.readString(workspace.resolve("ok.txt")));
        assertEquals("old\n", Files.readString(workspace.resolve("bad.txt")));
    }

    @Test
    void applyAllSucceeds(@TempDir Path workspace) throws Exception {
        Files.writeString(workspace.resolve("A.java"), "a\n");
        Files.writeString(workspace.resolve("B.java"), "b\n");
        String patch =
                """
                --- a/A.java
                +++ b/A.java
                @@ -1,1 +1,1 @@
                -a
                +A
                --- a/B.java
                +++ b/B.java
                @@ -1,1 +1,1 @@
                -b
                +B
                """;
        String err = MultiFilePatch.applyAll(workspace, MultiFilePatch.parse(patch));
        assertNull(err);
        assertEquals("A\n", Files.readString(workspace.resolve("A.java")));
        assertEquals("B\n", Files.readString(workspace.resolve("B.java")));
    }
}
