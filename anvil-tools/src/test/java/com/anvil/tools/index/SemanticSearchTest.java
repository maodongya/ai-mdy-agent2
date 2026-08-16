package com.anvil.tools.index;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticSearchTest {

    @Test
    void ranksMatchingChunks() {
        List<WorkspaceIndex.CodeChunk> chunks = List.of(
                new WorkspaceIndex.CodeChunk("a.java", 1, 10, "connect", "method", "public void connect() { open socket; }"),
                new WorkspaceIndex.CodeChunk("b.java", 1, 5, "render", "method", "void renderUi() {}"));
        List<SemanticSearch.ScoredChunk> hits = SemanticSearch.search(chunks, "connect socket", 5);
        assertFalse(hits.isEmpty());
        assertTrue(hits.get(0).chunk().symbolName().contains("connect"));
    }
}
