package com.anvil.core.compact;

import java.util.LinkedHashSet;
import java.util.Set;

/** Session anchors preserved across context compaction. */
public final class RunAnchors {

    private final LinkedHashSet<String> readFiles = new LinkedHashSet<>();
    private final LinkedHashSet<String> modifiedFiles = new LinkedHashSet<>();
    private String lastFailureSnippet = "";

    public void recordRead(String path) {
        if (path != null && !path.isBlank()) {
            readFiles.add(path.trim());
        }
    }

    public void recordWrite(String path) {
        if (path != null && !path.isBlank()) {
            modifiedFiles.add(path.trim());
        }
    }

    public void recordFailure(String snippet) {
        if (snippet != null && !snippet.isBlank()) {
            lastFailureSnippet = snippet.length() > 1200 ? snippet.substring(0, 1197) + "..." : snippet.trim();
        }
    }

    public boolean isEmpty() {
        return readFiles.isEmpty() && modifiedFiles.isEmpty() && lastFailureSnippet.isBlank();
    }

    public String formatBlock() {
        if (isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[session_anchors]\n");
        if (!readFiles.isEmpty()) {
            sb.append("read_files: ").append(joinLimited(readFiles, 24)).append('\n');
        }
        if (!modifiedFiles.isEmpty()) {
            sb.append("modified_files: ").append(joinLimited(modifiedFiles, 24)).append('\n');
        }
        if (!lastFailureSnippet.isBlank()) {
            sb.append("last_failure:\n").append(lastFailureSnippet).append('\n');
        }
        sb.append("Do not re-read or re-modify listed files unless necessary.");
        return sb.toString().trim();
    }

    private static String joinLimited(Set<String> items, int max) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String item : items) {
            if (n > 0) {
                sb.append(", ");
            }
            if (n >= max) {
                sb.append("… +").append(items.size() - max).append(" more");
                break;
            }
            sb.append(item);
            n++;
        }
        return sb.toString();
    }
}
