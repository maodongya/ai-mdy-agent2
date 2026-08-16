package com.anvil.tools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Fuzzy text matching for search_replace (Phase 7.1). */
public final class FuzzyMatcher {

    private static final double MIN_SCORE = 0.72;

    private FuzzyMatcher() {}

    public record Match(int start, int end, String text, double score) {}

    /** Find exact match first; returns null if not found. */
    public static Match exact(String haystack, String needle) {
        int idx = haystack.indexOf(needle);
        if (idx < 0) {
            return null;
        }
        return new Match(idx, idx + needle.length(), needle, 1.0);
    }

    /** Whitespace-normalized sliding window match. */
    public static Match normalized(String haystack, String needle) {
        if (needle.isBlank()) {
            return null;
        }
        String normNeedle = normalizeWs(needle);
        if (normNeedle.isEmpty()) {
            return null;
        }
        int window = Math.max(needle.length(), normNeedle.length());
        int maxWindow = Math.min(haystack.length(), needle.length() * 3 + 64);
        Match best = null;
        for (int len = window; len <= maxWindow && len <= haystack.length(); len++) {
            for (int start = 0; start + len <= haystack.length(); start++) {
                String slice = haystack.substring(start, start + len);
                if (normalizeWs(slice).equals(normNeedle)) {
                    return new Match(start, start + len, slice, 0.98);
                }
            }
        }
        return best;
    }

    /** Levenshtein-based near matches over line-bounded windows. */
    public static List<Match> nearMatches(String haystack, String needle, int topK) {
        if (needle == null || needle.isBlank() || haystack == null) {
            return List.of();
        }
        List<Match> candidates = new ArrayList<>();
        int needleLen = needle.length();
        int minLen = Math.max(1, (int) (needleLen * 0.6));
        int maxLen = Math.min(haystack.length(), (int) (needleLen * 1.6) + 8);

        for (int len = minLen; len <= maxLen; len++) {
            for (int start = 0; start + len <= haystack.length(); start++) {
                String slice = haystack.substring(start, start + len);
                double score = similarity(normalizeWs(slice), normalizeWs(needle));
                if (score >= MIN_SCORE) {
                    candidates.add(new Match(start, start + len, slice, score));
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(Match::score).reversed());
        List<Match> deduped = new ArrayList<>();
        for (Match m : candidates) {
            boolean overlaps = deduped.stream().anyMatch(d -> overlap(d, m));
            if (!overlaps) {
                deduped.add(m);
            }
            if (deduped.size() >= topK) {
                break;
            }
        }
        return deduped;
    }

    public static String formatCandidates(List<Match> matches, String content) {
        if (matches.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Closest matches:\n");
        int n = 0;
        for (Match m : matches) {
            if (n >= 3) {
                break;
            }
            int line = lineNumber(content, m.start());
            String preview = m.text().replace('\n', ' ').trim();
            if (preview.length() > 120) {
                preview = preview.substring(0, 117) + "...";
            }
            sb.append("- L")
                    .append(line)
                    .append(" score=")
                    .append(String.format(Locale.ROOT, "%.2f", m.score()))
                    .append(": ")
                    .append(preview)
                    .append('\n');
            n++;
        }
        return sb.toString().trim();
    }

    static String normalizeWs(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replaceAll("[ \\t]+", " ").replaceAll("\\n+", "\n").trim();
    }

    static double similarity(String a, String b) {
        if (a.equals(b)) {
            return 1.0;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        int dist = levenshtein(a, b);
        return 1.0 - (double) dist / Math.max(a.length(), b.length());
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }

    private static boolean overlap(Match a, Match b) {
        return a.start() < b.end() && b.start() < a.end();
    }

    private static int lineNumber(String content, int index) {
        int line = 1;
        for (int i = 0; i < index && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
