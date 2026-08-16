package com.anvil.ui;

import java.util.ArrayList;
import java.util.List;

/** Line-level diff for the Review Diff panel. */
final class DiffEngine {

    /** Guard against O(n^2) memory blow-ups on very large files. */
    static final int MAX_LINES = 4_000;

    private DiffEngine() {}

    static List<DiffReviewPanel.DiffRow> lines(String before, String after) {
        List<String> oldLines = splitLines(before);
        List<String> newLines = splitLines(after);
        if (oldLines.size() > MAX_LINES || newLines.size() > MAX_LINES) {
            return List.of(truncatedNotice(oldLines.size(), newLines.size()));
        }
        int m = oldLines.size();
        int n = newLines.size();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--) {
            for (int j = n - 1; j >= 0; j--) {
                if (oldLines.get(i).equals(newLines.get(j))) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }
        List<DiffReviewPanel.DiffRow> rows = new ArrayList<>();
        int i = 0;
        int j = 0;
        int lineNo = 1;
        while (i < m && j < n) {
            if (oldLines.get(i).equals(newLines.get(j))) {
                rows.add(row(lineNo++, " ", oldLines.get(i), newLines.get(j), DiffReviewPanel.DiffKind.CONTEXT));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                rows.add(row(lineNo++, "-", oldLines.get(i), "", DiffReviewPanel.DiffKind.REMOVED));
                i++;
            } else {
                rows.add(row(lineNo++, "+", "", newLines.get(j), DiffReviewPanel.DiffKind.ADDED));
                j++;
            }
        }
        while (i < m) {
            rows.add(row(lineNo++, "-", oldLines.get(i++), "", DiffReviewPanel.DiffKind.REMOVED));
        }
        while (j < n) {
            rows.add(row(lineNo++, "+", "", newLines.get(j++), DiffReviewPanel.DiffKind.ADDED));
        }
        return rows;
    }

    private static DiffReviewPanel.DiffRow row(
            int lineNo, String marker, String oldLine, String newLine, DiffReviewPanel.DiffKind kind) {
        return new DiffReviewPanel.DiffRow(lineNo, marker, oldLine, newLine, kind);
    }

    private static DiffReviewPanel.DiffRow truncatedNotice(int oldCount, int newCount) {
        String msg = "Diff truncated: " + oldCount + " / " + newCount + " lines (limit " + MAX_LINES + " per side).";
        return new DiffReviewPanel.DiffRow(1, "!", msg, msg, DiffReviewPanel.DiffKind.CONTEXT);
    }

    private static List<String> splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        return List.of(text.split("\n", -1));
    }
}
