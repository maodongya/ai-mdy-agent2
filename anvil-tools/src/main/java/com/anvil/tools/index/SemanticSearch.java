package com.anvil.tools.index;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** BM25-style lexical semantic search over code chunks (Phase 6.1, no external embedding API). */
public final class SemanticSearch {

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private SemanticSearch() {}

    public record ScoredChunk(WorkspaceIndex.CodeChunk chunk, double score) {}

    public static List<ScoredChunk> search(List<WorkspaceIndex.CodeChunk> chunks, String query, int topK) {
        if (chunks == null || chunks.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        List<String> queries = QueryExpander.expand(query);
        Set<String> queryTerms = queries.stream()
                .flatMap(q -> tokenize(q).stream())
                .collect(Collectors.toSet());
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        List<List<String>> docTokens = new ArrayList<>(chunks.size());
        Map<String, Integer> df = new HashMap<>();
        int totalLen = 0;
        for (WorkspaceIndex.CodeChunk chunk : chunks) {
            List<String> tokens = tokenize(chunk.text() + " " + chunk.symbolName() + " " + chunk.path());
            docTokens.add(tokens);
            totalLen += tokens.size();
            for (String term : Set.copyOf(tokens)) {
                df.merge(term, 1, Integer::sum);
            }
        }
        double avgLen = chunks.isEmpty() ? 0 : (double) totalLen / chunks.size();
        int n = chunks.size();

        List<ScoredChunk> scored = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            List<String> tokens = docTokens.get(i);
            if (tokens.isEmpty()) {
                continue;
            }
            Map<String, Integer> tf = new HashMap<>();
            for (String t : tokens) {
                tf.merge(t, 1, Integer::sum);
            }
            double score = 0;
            for (String term : queryTerms) {
                int termDf = df.getOrDefault(term, 0);
                if (termDf == 0) {
                    continue;
                }
                double idf = Math.log(1 + (n - termDf + 0.5) / (termDf + 0.5));
                int f = tf.getOrDefault(term, 0);
                double denom = f + K1 * (1 - B + B * tokens.size() / Math.max(1, avgLen));
                score += idf * (f * (K1 + 1)) / Math.max(1e-9, denom);
            }
            // Boost exact symbol / path matches
            WorkspaceIndex.CodeChunk c = chunks.get(i);
            String qLower = query.toLowerCase(Locale.ROOT);
            if (c.symbolName() != null && c.symbolName().toLowerCase(Locale.ROOT).contains(qLower)) {
                score += 4.0;
            }
            if (c.path().toLowerCase(Locale.ROOT).contains(qLower)) {
                score += 2.0;
            }
            if (score > 0) {
                scored.add(new ScoredChunk(c, score));
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        return scored.size() <= topK ? scored : scored.subList(0, topK);
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] parts = text.toLowerCase(Locale.ROOT)
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .split("[^a-z0-9_]+");
        List<String> tokens = new ArrayList<>();
        for (String p : parts) {
            if (p.length() >= 2) {
                tokens.add(p);
            }
        }
        return tokens;
    }
}
