package com.anvil.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticParserTest {

    @Test
    void parsesMavenCompilerFormat() {
        String output =
                """
                [ERROR] /project/src/main/java/Foo.java:[12,5] cannot find symbol
                  symbol:   variable bar
                [WARNING] /project/src/main/java/Bar.java:[3,1] unused import
                """;

        List<DiagnosticParser.Diagnostic> diagnostics = DiagnosticParser.parse(output);

        assertEquals(2, diagnostics.size());
        assertEquals("ERROR", diagnostics.get(0).severity());
        assertEquals("/project/src/main/java/Foo.java", diagnostics.get(0).file());
        assertEquals(12, diagnostics.get(0).line());
        assertEquals(5, diagnostics.get(0).column());
        assertTrue(diagnostics.get(0).message().contains("cannot find symbol"));
    }

    @Test
    void parsesJavacLineFormat() {
        String output = "src/Foo.java:42: error: ';' expected\n";

        List<DiagnosticParser.Diagnostic> diagnostics = DiagnosticParser.parse(output);

        assertEquals(1, diagnostics.size());
        assertEquals("src/Foo.java", diagnostics.get(0).file());
        assertEquals(42, diagnostics.get(0).line());
        assertEquals("ERROR", diagnostics.get(0).severity());
    }

    @Test
    void formatLimitsItems() {
        List<DiagnosticParser.Diagnostic> diagnostics = List.of(
                new DiagnosticParser.Diagnostic("A.java", 1, 0, "ERROR", "a"),
                new DiagnosticParser.Diagnostic("B.java", 2, 0, "ERROR", "b"),
                new DiagnosticParser.Diagnostic("C.java", 3, 0, "ERROR", "c"));

        String formatted = DiagnosticParser.format(diagnostics, 2);

        assertTrue(formatted.contains("A.java"));
        assertTrue(formatted.contains("B.java"));
        assertTrue(formatted.contains("1 more"));
    }
}
