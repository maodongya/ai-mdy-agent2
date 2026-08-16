package com.anvil.tools.lsp;

import com.anvil.tools.index.IndexStore;
import com.anvil.tools.index.WorkspaceIndex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Index-based symbol navigation fallback when jdtls is unavailable (Phase 9.2). */
public final class SymbolNavigation {

    private static final Pattern IDENT = Pattern.compile("[A-Za-z_\\$][A-Za-z0-9_\\$]*");

    private SymbolNavigation() {}

    public static Optional<LspLocation> definition(Path workspace, String relativePath, int line, int column) {
        String token = tokenAt(workspace, relativePath, line, column);
        if (token.isBlank()) {
            return Optional.empty();
        }
        WorkspaceIndex index = loadIndex(workspace);
        String q = token.toLowerCase(Locale.ROOT);
        return index.symbols().stream()
                .filter(s -> s.name().equalsIgnoreCase(token))
                .min(Comparator.comparingInt((WorkspaceIndex.SymbolEntry s) -> exactness(s.name(), q))
                        .thenComparing(s -> s.path().equals(relativePath) ? 0 : 1)
                        .thenComparingInt(WorkspaceIndex.SymbolEntry::line))
                .map(s -> new LspLocation(s.path(), s.line(), 1, "index"));
    }

    public static List<LspLocation> references(Path workspace, String relativePath, int line, int column) {
        String token = tokenAt(workspace, relativePath, line, column);
        if (token.isBlank()) {
            return List.of();
        }
        WorkspaceIndex index = loadIndex(workspace);
        List<LspLocation> hits = new ArrayList<>();
        for (WorkspaceIndex.SymbolEntry sym : index.symbols()) {
            if (sym.name().equalsIgnoreCase(token)) {
                hits.add(new LspLocation(sym.path(), sym.line(), 1, "index"));
            }
        }
        hits.sort(Comparator.comparing(LspLocation::path).thenComparingInt(LspLocation::line));
        return hits;
    }

    static String tokenAt(Path workspace, String relativePath, int line, int column) {
        if (relativePath == null || relativePath.isBlank() || line <= 0) {
            return "";
        }
        try {
            Path file = workspace.resolve(relativePath).normalize();
            if (!file.startsWith(workspace) || !java.nio.file.Files.isRegularFile(file)) {
                return "";
            }
            List<String> lines = java.nio.file.Files.readAllLines(file);
            if (line > lines.size()) {
                return "";
            }
            String text = lines.get(line - 1);
            if (text.isBlank()) {
                return "";
            }
            int col = Math.max(0, Math.min(column, text.length()) - 1);
            Matcher m = IDENT.matcher(text);
            while (m.find()) {
                if (m.start() <= col && m.end() > col) {
                    return m.group();
                }
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private static WorkspaceIndex loadIndex(Path workspace) {
        try {
            return IndexStore.load(workspace);
        } catch (Exception e) {
            return WorkspaceIndex.empty();
        }
    }

    private static int exactness(String name, String q) {
        if (name.equalsIgnoreCase(q)) {
            return 0;
        }
        if (name.toLowerCase(Locale.ROOT).startsWith(q)) {
            return 1;
        }
        return 2;
    }
}
