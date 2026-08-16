package com.anvil.core.instructions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Aggregates AGENTS.md and .cursor/rules (Codex/Cursor-style). */
public final class InstructionLoader {

    private static final int MAX_DEPTH = 5;
    private static final String AGENTS_FILE = "AGENTS.md";

    private InstructionLoader() {}

    public static String loadForWorkspace(Path workspaceRoot) {
        List<String> sections = new ArrayList<>();
        Path current = workspaceRoot.toAbsolutePath().normalize();
        int depth = 0;
        while (current != null && depth < MAX_DEPTH) {
            Path agents = current.resolve(AGENTS_FILE);
            if (Files.isRegularFile(agents)) {
                readSection(sections, agents);
            }
            Path cursorRules = current.resolve(".cursor/rules");
            if (Files.isDirectory(cursorRules)) {
                loadCursorRules(sections, cursorRules);
            }
            Path parent = current.getParent();
            if (parent == null || parent.equals(current)) {
                break;
            }
            current = parent;
            depth++;
        }
        Collections.reverse(sections);
        return String.join("\n\n", sections);
    }

    private static void loadCursorRules(List<String> sections, Path rulesDir) {
        try (Stream<Path> files = Files.list(rulesDir)) {
            files.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> readSection(sections, p));
        } catch (IOException ignored) {
            // skip
        }
    }

    private static void readSection(List<String> sections, Path file) {
        try {
            sections.add("# From " + file + "\n" + Files.readString(file).trim());
        } catch (IOException ignored) {
            // skip unreadable
        }
    }
}
