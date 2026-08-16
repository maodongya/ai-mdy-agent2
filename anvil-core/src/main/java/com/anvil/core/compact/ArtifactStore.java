package com.anvil.core.compact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Spill large tool outputs to disk; history keeps only a reference (Phase 11.2). */
public final class ArtifactStore {

    private static final int SPILL_THRESHOLD_CHARS = 10_000;

    private ArtifactStore() {}

    public record SpillResult(String historyContent, String artifactRef) {}

    public static SpillResult maybeSpill(Path workspaceRoot, String runId, String toolCallId, String content) {
        if (content == null || content.length() <= SPILL_THRESHOLD_CHARS) {
            return new SpillResult(content, null);
        }
        try {
            Path dir = workspaceRoot.resolve(".anvil/artifacts").resolve(runId);
            Files.createDirectories(dir);
            String safeId = toolCallId == null ? "tool" : toolCallId.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path file = dir.resolve(safeId + ".txt");
            Files.writeString(file, content);
            String ref = workspaceRoot.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
            String preview = ContextCompactor.truncateContent(content, 400);
            String history =
                    preview + "\n\n[artifact_ref: " + ref + " (" + content.length() + " chars; use fs.read to load)]";
            return new SpillResult(history, ref);
        } catch (IOException e) {
            return new SpillResult(ContextCompactor.truncateContent(content, 8_000), null);
        }
    }
}
