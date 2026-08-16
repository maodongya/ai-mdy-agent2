package com.anvil.tools.index;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryExpanderTest {

    @Test
    void expandsCamelAndSnake() {
        List<String> variants = QueryExpander.expand("AnvilClient");
        assertTrue(variants.contains("AnvilClient"));
        assertTrue(variants.stream().anyMatch(v -> v.contains("anvil") || v.contains("client")));
    }

    @Test
    void expandsConnectionSynonyms() {
        List<String> variants = QueryExpander.expand("connection logic");
        assertTrue(variants.stream().anyMatch(v -> v.toLowerCase().contains("connect") || v.contains("Client")));
    }

    @Test
    void toSnakeConvertsCamelCase() {
        assertTrue(QueryExpander.toSnake("AnvilClient").contains("anvil"));
    }
}
