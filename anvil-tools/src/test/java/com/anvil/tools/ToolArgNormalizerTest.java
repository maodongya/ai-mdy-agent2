package com.anvil.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolArgNormalizerTest {

    @Test
    void aliasesPathAndContent() {
        Map<String, Object> out = ToolArgNormalizer.normalize("fs.write", Map.of(
                "file_path", "src/Foo.java",
                "text", "class Foo {}"));
        assertEquals("src/Foo.java", out.get("path"));
        assertEquals("class Foo {}", out.get("content"));
    }

    @Test
    void filenameFallbackForWrite() {
        Map<String, Object> out = ToolArgNormalizer.normalize("fs.write", Map.of(
                "filename", "out.txt",
                "content", "hi"));
        assertEquals("out.txt", out.get("path"));
    }
}
