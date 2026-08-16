package com.anvil.tools.index;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Persists workspace index under {@code .anvil/workspace-index.json}. */
public final class IndexStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String INDEX_FILE = ".anvil/workspace-index.json";

    private IndexStore() {}

    public static Path indexPath(Path workspaceRoot) {
        return workspaceRoot.toAbsolutePath().normalize().resolve(INDEX_FILE);
    }

    public static WorkspaceIndex load(Path workspaceRoot) {
        Path file = indexPath(workspaceRoot);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            StoredIndex stored = MAPPER.readValue(Files.readString(file), StoredIndex.class);
            if (stored.version != WorkspaceIndex.CURRENT_VERSION) {
                return null;
            }
            Map<String, Set<String>> trigrams = rebuildTrigrams(stored.paths);
            List<WorkspaceIndex.SymbolEntry> symbols = stored.symbols.stream()
                    .map(s -> new WorkspaceIndex.SymbolEntry(
                            s.path,
                            s.line,
                            s.kind,
                            s.name,
                            s.superName,
                            s.interfaces != null ? s.interfaces : List.of()))
                    .toList();
            List<WorkspaceIndex.CodeChunk> chunks = stored.chunks == null
                    ? List.of()
                    : stored.chunks.stream()
                            .map(c -> new WorkspaceIndex.CodeChunk(
                                    c.path, c.startLine, c.endLine, c.symbolName, c.kind, c.text))
                            .toList();
            return new WorkspaceIndex(
                    stored.version, stored.builtAtMillis, List.copyOf(stored.paths), symbols, trigrams, chunks);
        } catch (IOException e) {
            return null;
        }
    }

    public static void save(Path workspaceRoot, WorkspaceIndex index) throws IOException {
        Path file = indexPath(workspaceRoot);
        Files.createDirectories(file.getParent());
        StoredIndex stored = new StoredIndex();
        stored.version = index.version();
        stored.builtAtMillis = index.builtAtMillis();
        stored.paths = index.paths();
        stored.symbols = index.symbols().stream()
                .map(s -> {
                    StoredSymbol sym = new StoredSymbol();
                    sym.path = s.path();
                    sym.line = s.line();
                    sym.kind = s.kind();
                    sym.name = s.name();
                    sym.superName = s.superName();
                    sym.interfaces = s.interfaces();
                    return sym;
                })
                .toList();
        stored.chunks = index.chunks().stream()
                .map(c -> {
                    StoredChunk chunk = new StoredChunk();
                    chunk.path = c.path();
                    chunk.startLine = c.startLine();
                    chunk.endLine = c.endLine();
                    chunk.symbolName = c.symbolName();
                    chunk.kind = c.kind();
                    chunk.text = c.text();
                    return chunk;
                })
                .toList();
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), stored);
    }

    public static boolean isStale(Path workspaceRoot, WorkspaceIndex index) {
        if (index == null) {
            return true;
        }
        if (index.version() != WorkspaceIndex.CURRENT_VERSION) {
            return true;
        }
        if (!Files.isRegularFile(indexPath(workspaceRoot))) {
            return true;
        }
        try {
            Path pom = workspaceRoot.resolve("pom.xml");
            if (Files.isRegularFile(pom)
                    && Files.getLastModifiedTime(pom).toMillis() > index.builtAtMillis()) {
                return true;
            }
        } catch (IOException ignored) {
            return true;
        }
        long ageMs = System.currentTimeMillis() - index.builtAtMillis();
        return ageMs > 3_600_000L;
    }

    private static Map<String, Set<String>> rebuildTrigrams(List<String> paths) {
        Map<String, Set<String>> trigrams = new HashMap<>();
        for (String path : paths) {
            String lower = path.toLowerCase();
            for (int i = 0; i + 3 <= lower.length(); i++) {
                String tri = lower.substring(i, i + 3);
                trigrams.computeIfAbsent(tri, k -> new HashSet<>()).add(path);
            }
            String base = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            trigrams.computeIfAbsent(base.toLowerCase(), k -> new HashSet<>()).add(path);
        }
        return Map.copyOf(trigrams);
    }

    static final class StoredIndex {
        public int version;
        public long builtAtMillis;
        public List<String> paths;
        public List<StoredSymbol> symbols;
        public List<StoredChunk> chunks;
    }

    static final class StoredSymbol {
        public String path;
        public int line;
        public String kind;
        public String name;
        public String superName;
        public List<String> interfaces;
    }

    static final class StoredChunk {
        public String path;
        public int startLine;
        public int endLine;
        public String symbolName;
        public String kind;
        public String text;
    }
}
