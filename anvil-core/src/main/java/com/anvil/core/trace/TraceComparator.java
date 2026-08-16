package com.anvil.core.trace;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Compares Anvil/Cursor run traces for regression analysis (Phase 8.5). */
public final class TraceComparator {

    private TraceComparator() {}

    public record TraceDiff(
            int expectedCount,
            int actualCount,
            int matchedInOrder,
            List<String> missingInActual,
            List<String> extraInActual,
            double similarity) {}

    /** Compare ordered event type sequences (ignoring message.delta). */
    public static TraceDiff compare(List<String> expected, List<String> actual) {
        List<String> exp = normalize(expected);
        List<String> act = normalize(actual);
        int matched = longestCommonSubsequenceLength(exp, act);
        Set<String> expSet = new LinkedHashSet<>(exp);
        Set<String> actSet = new LinkedHashSet<>(act);
        List<String> missing = new ArrayList<>();
        for (String e : expSet) {
            if (!actSet.contains(e)) {
                missing.add(e);
            }
        }
        List<String> extra = new ArrayList<>();
        for (String a : actSet) {
            if (!expSet.contains(a)) {
                extra.add(a);
            }
        }
        double similarity = exp.isEmpty() ? (act.isEmpty() ? 1.0 : 0.0) : (double) matched / exp.size();
        return new TraceDiff(exp.size(), act.size(), matched, List.copyOf(missing), List.copyOf(extra), similarity);
    }

    public static String formatReport(String label, TraceDiff diff) {
        StringBuilder sb = new StringBuilder();
        sb.append(label)
                .append(": similarity=")
                .append(String.format("%.0f%%", diff.similarity() * 100))
                .append(" matched=")
                .append(diff.matchedInOrder())
                .append('/')
                .append(diff.expectedCount())
                .append(" actual=")
                .append(diff.actualCount())
                .append('\n');
        if (!diff.missingInActual().isEmpty()) {
            sb.append("  missing: ").append(diff.missingInActual()).append('\n');
        }
        if (!diff.extraInActual().isEmpty()) {
            sb.append("  extra: ").append(diff.extraInActual()).append('\n');
        }
        return sb.toString().trim();
    }

    private static List<String> normalize(List<String> types) {
        if (types == null) {
            return List.of();
        }
        return types.stream().filter(t -> t != null && !"message.delta".equals(t)).toList();
    }

    private static int longestCommonSubsequenceLength(List<String> a, List<String> b) {
        int m = a.size();
        int n = b.size();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (a.get(i - 1).equals(b.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return dp[m][n];
    }
}
