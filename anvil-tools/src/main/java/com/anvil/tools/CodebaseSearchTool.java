package com.anvil.tools;

import com.anvil.protocol.ErrorCodes;
import com.anvil.protocol.ErrorInfo;
import com.anvil.protocol.ToolResult;
import com.anvil.tools.index.IndexService;
import com.anvil.tools.index.QueryExpander;
import com.anvil.tools.index.SemanticSearch;
import com.anvil.tools.index.WorkspaceIndex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Indexed workspace search: paths, symbols, semantic chunks, and content snippets. */
public final class CodebaseSearchTool {

    private static final int DEFAULT_TOP_K = 20;

    private CodebaseSearchTool() {}

    public static ToolResult search(Path workspaceRoot, String toolCallId, String query, Integer topK) {
        if (query == null || query.isBlank()) {
            return new ToolResult(
                    toolCallId,
                    "codebase.search",
                    "error",
                    "",
                    false,
                    null,
                    ErrorInfo.of(ErrorCodes.TOOL_ARG_INVALID, "query is required", false));
        }
        int k = topK == null || topK <= 0 ? DEFAULT_TOP_K : Math.min(topK, 50);
        String q = query.trim();
        String qLower = q.toLowerCase(Locale.ROOT);

        WorkspaceIndex index = IndexService.get(workspaceRoot);
        Map<String, HitScore> scores = new LinkedHashMap<>();

        scorePathMatches(index, qLower, scores);
        scoreSymbolMatches(index, qLower, scores);
        scoreSemanticMatches(index, q, k, scores);

        LinkedHashSet<String> grepPatterns = new LinkedHashSet<>();
        grepPatterns.add(q);
        for (String variant : QueryExpander.expand(q)) {
            grepPatterns.add(variant);
        }

        boolean anyTruncated = false;
        int variantIndex = 0;
        for (String pattern : grepPatterns) {
            if (variantIndex >= 4) {
                break;
            }
            String regex = java.util.regex.Pattern.quote(pattern);
            ToolResult raw = GrepTool.grep(workspaceRoot, toolCallId + "_grep_" + variantIndex, regex, "**/*", true, 300);
            anyTruncated = anyTruncated || raw.truncated();
            int weight = variantIndex == 0 ? 3 : 1;
            mergeGrepSnippets(parseGrepSnippets(raw.content()), scores, weight);
            variantIndex++;
        }

        if (scores.isEmpty()) {
            return ToolResult.ok(toolCallId, "codebase.search", "no matches for: " + q);
        }

        List<HitScore> ranked = new ArrayList<>(scores.values());
        ranked.sort(Comparator.comparingInt((HitScore h) -> h.totalScore()).reversed()
                .thenComparing(h -> h.path));

        StringBuilder sb = new StringBuilder();
        int files = 0;
        for (HitScore hit : ranked) {
            if (files >= k) {
                break;
            }
            sb.append(hit.path).append(" (score=").append(hit.totalScore()).append(")\n");
            if (!hit.snippets.isEmpty()) {
                for (Snippet sn : hit.snippets.stream().limit(2).toList()) {
                    sb.append("  L").append(sn.startLine());
                    if (sn.endLine() > sn.startLine()) {
                        sb.append('-').append(sn.endLine());
                    }
                    sb.append(": ").append(sn.preview()).append('\n');
                }
            } else if (!hit.symbolHints.isEmpty()) {
                for (String hint : hit.symbolHints.stream().limit(2).toList()) {
                    sb.append("  symbol: ").append(hint).append('\n');
                }
            }
            if (hit.semanticHits > 0) {
                sb.append("  semantic: ").append(hit.semanticHits).append(" chunk(s)\n");
            }
            files++;
        }

        boolean truncated = ranked.size() > k || anyTruncated;
        if (truncated) {
            sb.append("...[top ").append(k).append(" of ").append(ranked.size()).append(" files]");
        }
        return new ToolResult(toolCallId, "codebase.search", "ok", sb.toString().trim(), truncated, null, null);
    }

    private static void scoreSemanticMatches(
            WorkspaceIndex index, String query, int topK, Map<String, HitScore> scores) {
        if (index.chunks().isEmpty()) {
            return;
        }
        for (SemanticSearch.ScoredChunk sc : SemanticSearch.search(index.chunks(), query, topK * 2)) {
            HitScore hit = scores.computeIfAbsent(sc.chunk().path(), HitScore::new);
            hit.semanticScore += sc.score();
            hit.semanticHits++;
            String preview = sc.chunk().text().replace('\n', ' ').trim();
            if (preview.length() > 120) {
                preview = preview.substring(0, 117) + "...";
            }
            hit.snippets.add(new Snippet(sc.chunk().startLine(), sc.chunk().endLine(), preview));
        }
    }

    private static void scorePathMatches(WorkspaceIndex index, String qLower, Map<String, HitScore> scores) {
        for (String path : index.paths()) {
            if (path.toLowerCase(Locale.ROOT).contains(qLower)) {
                HitScore hit = scores.computeIfAbsent(path, HitScore::new);
                hit.pathMatch = true;
            }
        }
        Set<String> triPaths = index.pathTrigrams().get(qLower);
        if (triPaths != null) {
            for (String path : triPaths) {
                HitScore hit = scores.computeIfAbsent(path, HitScore::new);
                hit.trigramMatch = true;
            }
        }
    }

    private static void scoreSymbolMatches(WorkspaceIndex index, String qLower, Map<String, HitScore> scores) {
        for (WorkspaceIndex.SymbolEntry sym : index.symbols()) {
            if (sym.name().toLowerCase(Locale.ROOT).contains(qLower)) {
                HitScore hit = scores.computeIfAbsent(sym.path(), HitScore::new);
                hit.symbolHits++;
                hit.symbolHints.add(sym.kind() + " " + sym.name() + " L" + sym.line());
            }
        }
    }

    private static void mergeGrepSnippets(
            Map<String, List<Snippet>> contentHits, Map<String, HitScore> scores, int weight) {
        for (var entry : contentHits.entrySet()) {
            HitScore hit = scores.computeIfAbsent(entry.getKey(), HitScore::new);
            hit.contentMatches += entry.getValue().size() * weight;
            hit.snippets.addAll(entry.getValue());
        }
    }

    private static Map<String, List<Snippet>> parseGrepSnippets(String content) {
        Map<String, List<Snippet>> map = new LinkedHashMap<>();
        if (content == null || content.isBlank() || "no matches".equals(content)) {
            return map;
        }
        for (String line : content.split("\n")) {
            if (line.startsWith("...[")) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            int second = line.indexOf(':', colon + 1);
            if (second <= 0) {
                continue;
            }
            String path = line.substring(0, colon);
            int lineNo;
            try {
                lineNo = Integer.parseInt(line.substring(colon + 1, second));
            } catch (NumberFormatException e) {
                continue;
            }
            String preview = line.substring(second + 1).trim();
            map.computeIfAbsent(path, p -> new ArrayList<>()).add(new Snippet(lineNo, lineNo, preview));
        }
        return map;
    }

    private record Snippet(int startLine, int endLine, String preview) {}

    private static final class HitScore {
        final String path;
        boolean pathMatch;
        boolean trigramMatch;
        int symbolHits;
        int contentMatches;
        double semanticScore;
        int semanticHits;
        final List<String> symbolHints = new ArrayList<>();
        final List<Snippet> snippets = new ArrayList<>();

        HitScore(String path) {
            this.path = path;
        }

        int totalScore() {
            int score = contentMatches * 3;
            if (pathMatch) {
                score += 10;
            }
            if (trigramMatch) {
                score += 5;
            }
            score += symbolHits * 8;
            score += (int) (semanticScore * 6);
            score += semanticHits * 4;
            return score;
        }
    }
}
