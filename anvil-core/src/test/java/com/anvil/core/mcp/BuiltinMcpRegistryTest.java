package com.anvil.core.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinMcpRegistryTest {

    @Test
    void exposesSchemasForAllowlist() {
        var schemas = BuiltinMcpRegistry.toolSchemas(Set.of("junit", "github", "checkstyle"));
        assertEquals(3, schemas.size());
        assertTrue(schemas.stream().anyMatch(s -> "mcp.junit.report".equals(name(s))));
    }

    @Test
    void executesStubTools() {
        var result = BuiltinMcpRegistry.execute("tc1", "mcp.junit.report", java.util.Map.of("path", "target/surefire-reports"));
        assertEquals("ok", result.status());
        assertTrue(result.content().contains("JUnit report"));
    }

    @SuppressWarnings("unchecked")
    private static String name(Map<String, Object> schema) {
        Map<String, Object> fn = (Map<String, Object>) schema.get("function");
        return String.valueOf(fn.get("name"));
    }
}
