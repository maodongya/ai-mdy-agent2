package com.anvil.ui;

import java.util.ArrayList;
import java.util.List;

/** Line-level diff and hunk grouping for the Review Diff panel (Phase 7.3). */
final class DiffEngine {

    /** Guard against O(n^2) memory blow-ups on very large files. */
    static final int MAX_LINES = 4_000;

    enum HunkDecision { PENDING, ACCEPTED, REJECTED }

    record DiffHunk(
            int index,
            int rowStart,
            int rowEnd,
            int added,
            int removed,
            String patch,
            HunkDecision decision) {}

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

    static List<DiffHunk> hunks(List<DiffReviewPanel.DiffRow> rows) {
        List<DiffHunk> out = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return out;
        }
        int start = -1;
        int hunkIdx = 0;
        for (int i = 0; i < rows.size(); i++) {
            DiffReviewPanel.DiffRow r = rows.get(i);
            boolean change = r.kind() != DiffReviewPanel.DiffKind.CONTEXT;
            if (change && start < 0) {
                start = Math.max(0, i - 1);
            }
            if (start >= 0 && (!change || i == rows.size() - 1)) {
                int end = change && i == rows.size() - 1 ? i : i - 1;
                if (end >= start && hasChange(rows, start, end)) {
                    out.add(buildHunk(hunkIdx++, start, end, rows));
                }
                start = -1;
            }
        }
        return out;
    }

    /** Rebuild file content from hunk decisions (REJECTED hunks revert to before side). */
    static String rebuild(String before, String after, List<DiffReviewPanel.DiffRow> rows, List<DiffHunk> hunks) {
        if (hunks == null || hunks.isEmpty()) {
            return after;
        }
        if (hunks.stream().allMatch(h -> h.decision() == HunkDecision.REJECTED)) {
            return before;
        }
        String current = after;
        for (DiffHunk hunk : hunks) {
            if (hunk.decision() == HunkDecision.REJECTED) {
                String reverted = applyHunkPatch(current, invertPatch(hunk.patch()));
                if (reverted != null) {
                    current = reverted;
                }
            }
        }
        return current;
    }

    static boolean allHunksResolved(List<DiffHunk> hunks) {
        return hunks.stream().noneMatch(h -> h.decision() == HunkDecision.PENDING);
    }

    static DiffHunk withDecision(DiffHunk hunk, HunkDecision decision) {
        return new DiffHunk(
                hunk.index(), hunk.rowStart(), hunk.rowEnd(), hunk.added(), hunk.removed(), hunk.patch(), decision);
    }

    private static String applyHunkPatch(String original, String patch) {
        List<String> lines = new ArrayList<>(splitLines(original));
        if (lines.size() > 1 && lines.get(lines.size() - 1).isEmpty() && !original.endsWith("\n")) {
            lines.remove(lines.size() - 1);
        }
        List<String> oldBlock = new ArrayList<>();
        List<String> newBlock = new ArrayList<>();
        for (String line : patch.split("\n", -1)) {
            if (line.isEmpty() || line.startsWith("@@")) {
                continue;
            }
            if (line.charAt(0) == ' ') {
                String text = line.substring(1);
                oldBlock.add(text);
                newBlock.add(text);
            } else if (line.charAt(0) == '-') {
                oldBlock.add(line.substring(1));
            } else if (line.charAt(0) == '+') {
                newBlock.add(line.substring(1));
            }
        }
        if (oldBlock.isEmpty()) {
            return original;
        }
        int startIdx = findBlock(lines, oldBlock);
        if (startIdx < 0) {
            return null;
        }
        lines.subList(startIdx, startIdx + oldBlock.size()).clear();
        lines.addAll(startIdx, newBlock);
        return String.join("\n", lines);
    }

    private static int findBlock(List<String> lines, List<String> block) {
        for (int start = 0; start <= lines.size() - block.size(); start++) {
            boolean match = true;
            for (int i = 0; i < block.size(); i++) {
                if (!lines.get(start + i).equals(block.get(i))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return start;
            }
        }
        return -1;
    }

    private static boolean hasChange(List<DiffReviewPanel.DiffRow> rows, int start, int end) {
        for (int i = start; i <= end; i++) {
            if (rows.get(i).kind() != DiffReviewPanel.DiffKind.CONTEXT) {
                return true;
            }
        }
        return false;
    }

    private static DiffHunk buildHunk(int index, int start, int end, List<DiffReviewPanel.DiffRow> rows) {
        int added = 0;
        int removed = 0;
        StringBuilder patch = new StringBuilder("@@\n");
        for (int i = start; i <= end; i++) {
            DiffReviewPanel.DiffRow r = rows.get(i);
            if (r.kind() == DiffReviewPanel.DiffKind.REMOVED) {
                patch.append('-').append(r.oldLine()).append('\n');
                removed++;
            } else if (r.kind() == DiffReviewPanel.DiffKind.ADDED) {
                patch.append('+').append(r.newLine()).append('\n');
                added++;
            } else {
                patch.append(' ').append(r.oldLine()).append('\n');
            }
        }
        return new DiffHunk(index, start, end, added, removed, patch.toString(), HunkDecision.PENDING);
    }

    private static String invertPatch(String patch) {
        if (patch == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder("@@\n");
        for (String line : patch.split("\n", -1)) {
            if (line.startsWith("+")) {
                sb.append('-').append(line.substring(1)).append('\n');
            } else if (line.startsWith("-")) {
                sb.append('+').append(line.substring(1)).append('\n');
            } else if (line.startsWith(" ") || line.startsWith("@@")) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
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
