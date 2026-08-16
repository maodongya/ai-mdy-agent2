package com.anvil.tools;

import java.util.ArrayList;
import java.util.List;

/** Minimal unified diff for edit preview (Phase 5). */
public final class TextDiff {

    private static final int MAX_DIFF_CHARS = 8_000;

    private TextDiff() {}

    public static String unified(String before, String after, int contextLines) {
        List<String> oldLines = lines(before);
        List<String> newLines = lines(after);
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
        List<String> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < m && j < n) {
            if (oldLines.get(i).equals(newLines.get(j))) {
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                out.add("- " + oldLines.get(i));
                i++;
            } else {
                out.add("+ " + newLines.get(j));
                j++;
            }
        }
        while (i < m) {
            out.add("- " + oldLines.get(i++));
        }
        while (j < n) {
            out.add("+ " + newLines.get(j++));
        }
        if (out.isEmpty()) {
            return "(no changes)";
        }
        String diff = String.join("\n", out);
        if (diff.length() > MAX_DIFF_CHARS) {
            return diff.substring(0, MAX_DIFF_CHARS) + "\n… [diff truncated]";
        }
        return diff;
    }

    private static List<String> lines(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        return List.of(text.split("\n", -1));
    }
}
