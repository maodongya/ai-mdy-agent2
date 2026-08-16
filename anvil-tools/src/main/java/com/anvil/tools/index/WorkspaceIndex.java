package com.anvil.tools.index;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** In-memory workspace index: paths, Java symbols, path trigrams, semantic code chunks. */
public record WorkspaceIndex(
        int version,
        long builtAtMillis,
        List<String> paths,
        List<SymbolEntry> symbols,
        Map<String, Set<String>> pathTrigrams,
        List<CodeChunk> chunks) {

    public static final int CURRENT_VERSION = 2;

    /** Java symbol with optional type hierarchy (Phase 6). */
    public record SymbolEntry(
            String path,
            int line,
            String kind,
            String name,
            String superName,
            List<String> interfaces) {

        public SymbolEntry(String path, int line, String kind, String name) {
            this(path, line, kind, name, null, List.of());
        }
    }

    /** Searchable code fragment (class/method level) for semantic retrieval. */
    public record CodeChunk(String path, int startLine, int endLine, String symbolName, String kind, String text) {}

    public WorkspaceIndex {
        chunks = chunks != null ? chunks : List.of();
    }

    /** Backward-compatible ctor without chunks (v1). */
    public WorkspaceIndex(
            int version,
            long builtAtMillis,
            List<String> paths,
            List<SymbolEntry> symbols,
            Map<String, Set<String>> pathTrigrams) {
        this(version, builtAtMillis, paths, symbols, pathTrigrams, List.of());
    }

    public static WorkspaceIndex empty() {
        return new WorkspaceIndex(CURRENT_VERSION, 0L, List.of(), List.of(), Map.of(), List.of());
    }
}
