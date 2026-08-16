package com.anvil.tools.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexBuilderTest {

    @Test
    void buildsPathsAndJavaSymbols(@TempDir Path workspace) throws Exception {
        Path src = workspace.resolve("src/main/java/com/example/Foo.java");
        Files.createDirectories(src.getParent());
        Files.writeString(
                src,
                """
                package com.example;
                public class Foo {
                    public void bar() {}
                }
                """);

        WorkspaceIndex index = IndexBuilder.build(workspace);

        assertTrue(index.paths().stream().anyMatch(p -> p.endsWith("Foo.java")));
        assertTrue(index.symbols().stream().anyMatch(s -> "Foo".equals(s.name()) && "class".equals(s.kind())));
        assertTrue(index.symbols().stream().anyMatch(s -> "bar".equals(s.name()) && "method".equals(s.kind())));
        assertFalse(index.chunks().isEmpty());
    }

    @Test
    void indexesExtendsAndImplements(@TempDir Path workspace) throws Exception {
        Path src = workspace.resolve("Impl.java");
        Files.writeString(
                src,
                """
                public class Impl extends Base implements Service, Runnable {
                    public Impl() {}
                }
                """);
        WorkspaceIndex index = IndexBuilder.build(workspace);
        WorkspaceIndex.SymbolEntry impl = index.symbols().stream()
                .filter(s -> "Impl".equals(s.name()))
                .findFirst()
                .orElseThrow();
        assertTrue("Base".equals(impl.superName()));
        assertTrue(impl.interfaces().contains("Service"));
        assertTrue(index.symbols().stream().anyMatch(s -> "constructor".equals(s.kind())));
    }

    @Test
    void persistsAndReloads(@TempDir Path workspace) throws Exception {
        Files.writeString(workspace.resolve("Hello.txt"), "hello");

        WorkspaceIndex built = IndexBuilder.build(workspace);
        IndexStore.save(workspace, built);

        WorkspaceIndex loaded = IndexStore.load(workspace);
        assertFalse(loaded.paths().isEmpty());
        assertFalse(IndexStore.isStale(workspace, loaded));
    }
}
