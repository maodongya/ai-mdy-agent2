package com.anvil.tools;

import com.anvil.protocol.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FsToolsTest {

    @TempDir
    Path workspace;

    private FsTools fs;

    @BeforeEach
    void setUp() throws Exception {
        Path src = workspace.resolve("src/Add.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, "public class Add { public static int add(int a,int b){return a+b;} }");
        fs = new FsTools(workspace);
    }

    @Test
    void readOk() {
        ToolResult r = fs.read("c1", "src/Add.java");
        assertEquals("ok", r.status());
        assertTrue(r.content().contains("class Add"));
    }

    @Test
    void readEscapeDenied() {
        ToolResult r = fs.read("c2", "../secret.txt");
        assertEquals("denied", r.status());
    }

    @Test
    void writeOk() {
        ToolResult r = fs.write("c3", "out.txt", "hello");
        assertEquals("ok", r.status());
    }

    @Test
    void readWithOffsetAndLimit() {
        ToolResult r = fs.read("c4", "src/Add.java", 1, 1);
        assertEquals("ok", r.status());
        assertEquals("public class Add { public static int add(int a,int b){return a+b;} }", r.content());
    }

    @Test
    void sliceLinesHelper() {
        String text = "a\nb\nc";
        assertEquals("b", FsTools.sliceLines(text, 2, 1));
        assertEquals("a\nb", FsTools.sliceLines(text, null, 2));
    }

    @Test
    void executeNormalizesFilePathAlias() {
        ToolResult r = FsTools.execute(fs, "fs.read", "c5", Map.of("filePath", "src/Add.java"));
        assertEquals("ok", r.status());
        assertTrue(r.content().contains("class Add"));
    }

    @Test
    void executeMissingPathReturnsErrorNotThrow() {
        ToolResult r = FsTools.execute(fs, "fs.write", "c6", Map.of("content", "only content"));
        assertEquals("error", r.status());
        assertTrue(r.error().message().contains("missing arg: path"));
    }
}
