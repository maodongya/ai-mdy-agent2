package com.anvil.tools.lsp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolNavigationTest {

    @TempDir
    Path workspace;

    @Test
    void findsDefinitionFromIndex() throws Exception {
        Path javaDir = workspace.resolve("src/main/java/demo");
        Files.createDirectories(javaDir);
        Files.writeString(
                javaDir.resolve("Foo.java"),
                """
                package demo;
                class Foo {
                    void bar() {}
                }
                """);
        Files.writeString(
                javaDir.resolve("Main.java"),
                """
                package demo;
                class Main {
                    void run() { Foo f = new Foo(); f.bar(); }
                }
                """);

        var index = com.anvil.tools.index.IndexBuilder.build(workspace);
        com.anvil.tools.index.IndexStore.save(workspace, index);

        var loc = SymbolNavigation.definition(workspace, "src/main/java/demo/Main.java", 3, 20);
        assertTrue(loc.isPresent());
        assertEquals("src/main/java/demo/Foo.java", loc.get().path());
    }

    @Test
    void tokenAtCursorExtractsIdentifier() throws Exception {
        Path file = workspace.resolve("X.java");
        Files.writeString(file, "class X { void hello() {} }");

        assertEquals("hello", SymbolNavigation.tokenAt(workspace, "X.java", 1, 18));
    }
}
