package com.anvil.core.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAiToolFormatTest {

    @Test
    void strictModeMapsDotsToUnderscores() {
        var tools = ToolCatalog.builtinSchemas(com.anvil.protocol.Mode.ASK);
        OpenAiToolFormat.NormalizedTools normalized = OpenAiToolFormat.normalize(tools, true);

        @SuppressWarnings("unchecked")
        Map<String, Object> fn = normalized.tools().stream()
                .map(t -> (Map<String, Object>) t.get("function"))
                .filter(f -> "fs_read".equals(f.get("name")))
                .findFirst()
                .orElseThrow();
        assertEquals("fs_read", fn.get("name"));
        assertEquals("fs.read", normalized.apiToCanonical().get("fs_read"));
    }

    @Test
    void resolveCanonicalNameMapsBack() {
        var normalized = OpenAiToolFormat.normalize(
                List.of(Map.of("name", "shell.exec", "description", "x", "parameters", Map.of())),
                true);
        assertEquals("shell.exec", OpenAiToolFormat.resolveCanonicalName("shell_exec", normalized.apiToCanonical()));
    }
}
