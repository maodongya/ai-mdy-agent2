package com.anvil.tools.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtReferenceParserTest {

    @Test
    void resolvesFilePathAndCleansMessage(@TempDir Path workspace) throws Exception {
        Path src = workspace.resolve("src/Foo.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, "class Foo {}");
        IndexService.invalidate(workspace);
        IndexService.warm(workspace);

        AtReferenceParser.Result result = AtReferenceParser.parse("fix bug in @src/Foo.java please", workspace);

        assertTrue(result.resolvedPaths().stream().anyMatch(p -> p.endsWith("Foo.java")));
        assertEquals("fix bug in please", result.cleanedMessage());
    }

    @Test
    void resolvesSymbolName(@TempDir Path workspace) throws Exception {
        Path src = workspace.resolve("com/example/Service.java");
        Files.createDirectories(src.getParent());
        Files.writeString(
                src,
                """
                package com.example;
                public interface Service {}
                """);
        IndexService.invalidate(workspace);
        IndexService.warm(workspace);

        AtReferenceParser.Result result = AtReferenceParser.parse("update @Service impl", workspace);
        assertTrue(result.resolvedPaths().stream().anyMatch(p -> p.endsWith("Service.java")));
    }
}
