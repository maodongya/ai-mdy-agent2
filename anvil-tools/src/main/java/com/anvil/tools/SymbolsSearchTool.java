package com.anvil.tools;

import com.anvil.protocol.ErrorCodes;
import com.anvil.protocol.ErrorInfo;
import com.anvil.protocol.ToolResult;
import com.anvil.tools.index.IndexService;
import com.anvil.tools.index.TypeGraph;
import com.anvil.tools.index.WorkspaceIndex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Java symbol lookup with type hierarchy (Phase 6.2). */
public final class SymbolsSearchTool {

    private static final int DEFAULT_TOP_K = 30;

    private SymbolsSearchTool() {}

    public static ToolResult search(Path workspaceRoot, String toolCallId, String query, Integer topK) {
        if (query == null || query.isBlank()) {
            return new ToolResult(
                    toolCallId,
                    "symbols.search",
                    "error",
                    "",
                    false,
                    null,
                    ErrorInfo.of(ErrorCodes.TOOL_ARG_INVALID, "query is required", false));
        }
        int k = topK == null || topK <= 0 ? DEFAULT_TOP_K : Math.min(topK, 100);
        String q = query.trim();
        String qLower = q.toLowerCase(Locale.ROOT);

        WorkspaceIndex index = IndexService.get(workspaceRoot);
        TypeGraph graph = TypeGraph.from(index);
        List<WorkspaceIndex.SymbolEntry> hits = index.symbols().stream()
                .filter(s -> s.name().toLowerCase(Locale.ROOT).contains(qLower))
                .sorted(Comparator.comparing((WorkspaceIndex.SymbolEntry s) -> exactness(s.name(), qLower))
                        .thenComparing(WorkspaceIndex.SymbolEntry::path)
                        .thenComparingInt(WorkspaceIndex.SymbolEntry::line))
                .limit(k)
                .toList();

        Set<String> related = new LinkedHashSet<>();
        List<String> implementors = graph.implementorsOf(q);
        List<String> subtypes = graph.subtypesOf(q);
        related.addAll(implementors);
        related.addAll(subtypes);

        if (hits.isEmpty() && related.isEmpty()) {
            return ToolResult.ok(toolCallId, "symbols.search", "no symbols for: " + query);
        }

        StringBuilder sb = new StringBuilder();
        for (WorkspaceIndex.SymbolEntry sym : hits) {
            sb.append(sym.kind())
                    .append(' ')
                    .append(sym.name())
                    .append(" — ")
                    .append(sym.path())
                    .append(':')
                    .append(sym.line());
            if (sym.superName() != null && !sym.superName().isBlank()) {
                sb.append(" extends ").append(sym.superName());
            }
            if (sym.interfaces() != null && !sym.interfaces().isEmpty()) {
                sb.append(" implements ").append(String.join(", ", sym.interfaces()));
            }
            sb.append('\n');
        }

        if (!implementors.isEmpty()) {
            sb.append("\nimplementors of ").append(q).append(":\n");
            for (String impl : implementors.stream().limit(20).toList()) {
                sb.append("- ").append(impl).append('\n');
            }
        }
        if (!subtypes.isEmpty()) {
            sb.append("\nsubtypes of ").append(q).append(":\n");
            for (String sub : subtypes.stream().limit(20).toList()) {
                sb.append("- ").append(sub).append('\n');
            }
        }

        boolean truncated = hits.size() >= k || implementors.size() > 20 || subtypes.size() > 20;
        return new ToolResult(toolCallId, "symbols.search", "ok", sb.toString().trim(), truncated, null, null);
    }

    private static int exactness(String name, String qLower) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.equals(qLower)) {
            return 0;
        }
        if (lower.startsWith(qLower)) {
            return 1;
        }
        return 2;
    }
}
