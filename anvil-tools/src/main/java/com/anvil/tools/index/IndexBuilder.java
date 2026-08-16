package com.anvil.tools.index;

import com.anvil.tools.WorkspaceWalk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds workspace path + Java symbol + semantic chunk index (Phase 6). */
public final class IndexBuilder {

    private static final int MAX_FILE_BYTES = 512_000;
    private static final int MAX_CHUNK_CHARS = 4_000;
    private static final int MAX_TYPE_LINES = 120;
    private static final int MAX_METHOD_LINES = 80;

    private static final Pattern TYPE_LINE = Pattern.compile(
            "^\\s*(?:public\\s+|private\\s+|protected\\s+)?"
                    + "(?:abstract\\s+|static\\s+|final\\s+|sealed\\s+|non-sealed\\s+)*"
                    + "(class|interface|enum|record)\\s+(\\w+)(.*)$");
    private static final Pattern EXTENDS = Pattern.compile("extends\\s+([\\w.]+)");
    private static final Pattern IMPLEMENTS = Pattern.compile("implements\\s+([\\w.,\\s]+)");
    private static final Pattern IFACE_EXTENDS = Pattern.compile("extends\\s+([\\w.,\\s]+)");
    private static final Pattern METHOD_DEF = Pattern.compile(
            "^\\s*(?:public|private|protected|static|\\s)+[\\w<>,\\[\\]\\s?]+\\s+(\\w+)\\s*\\([^;]*\\)");
    private static final Pattern CONSTRUCTOR = Pattern.compile(
            "^\\s*(?:public|protected|private)?\\s+(\\w+)\\s*\\([^;]*\\)\\s*(?:throws\\s+[\\w.,\\s]+)?\\s*\\{?");

    private static final Set<String> KEYWORDS =
            Set.of("if", "for", "while", "switch", "catch", "new", "return", "throw", "else");

    private IndexBuilder() {}

    public static WorkspaceIndex build(Path workspaceRoot) throws IOException {
        Path root = workspaceRoot.toAbsolutePath().normalize();
        List<String> paths = new ArrayList<>();
        List<WorkspaceIndex.SymbolEntry> symbols = new ArrayList<>();
        List<WorkspaceIndex.CodeChunk> chunks = new ArrayList<>();
        Map<String, Set<String>> trigrams = new HashMap<>();

        WorkspaceWalk.forEachFile(root, file -> {
            String rel = root.relativize(file).toString().replace('\\', '/');
            paths.add(rel);
            indexPathTrigrams(rel, trigrams);
            if (rel.endsWith(".java")) {
                try {
                    if (Files.size(file) > MAX_FILE_BYTES) {
                        return;
                    }
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    indexJavaFile(rel, lines, symbols, chunks);
                } catch (IOException ignored) {
                    // skip unreadable
                }
            }
        });

        paths.sort(Comparator.naturalOrder());
        symbols.sort(Comparator.comparing(WorkspaceIndex.SymbolEntry::path)
                .thenComparingInt(WorkspaceIndex.SymbolEntry::line));
        return new WorkspaceIndex(
                WorkspaceIndex.CURRENT_VERSION,
                System.currentTimeMillis(),
                List.copyOf(paths),
                List.copyOf(symbols),
                Map.copyOf(trigrams),
                List.copyOf(chunks));
    }

    private static void indexPathTrigrams(String path, Map<String, Set<String>> trigrams) {
        String lower = path.toLowerCase(Locale.ROOT);
        for (int i = 0; i + 3 <= lower.length(); i++) {
            String tri = lower.substring(i, i + 3);
            if (tri.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == '/' || ch == '.' || ch == '_')) {
                trigrams.computeIfAbsent(tri, k -> new HashSet<>()).add(path);
            }
        }
        String base = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        trigrams.computeIfAbsent(base.toLowerCase(Locale.ROOT), k -> new HashSet<>()).add(path);
    }

    private static void indexJavaFile(
            String path,
            List<String> lines,
            List<WorkspaceIndex.SymbolEntry> symbols,
            List<WorkspaceIndex.CodeChunk> chunks) {
        String currentType = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher typeMatcher = TYPE_LINE.matcher(line);
            if (typeMatcher.find()) {
                String kind = typeMatcher.group(1);
                String name = typeMatcher.group(2);
                String rest = typeMatcher.group(3) != null ? typeMatcher.group(3) : "";
                String superName = null;
                List<String> interfaces = List.of();
                if ("interface".equals(kind)) {
                    Matcher ifaceExt = IFACE_EXTENDS.matcher(rest);
                    if (ifaceExt.find()) {
                        interfaces = splitTypes(ifaceExt.group(1));
                    }
                } else {
                    Matcher ext = EXTENDS.matcher(rest);
                    if (ext.find()) {
                        superName = ext.group(1).trim();
                    }
                    Matcher imp = IMPLEMENTS.matcher(rest);
                    if (imp.find()) {
                        interfaces = splitTypes(imp.group(1));
                    }
                }
                symbols.add(new WorkspaceIndex.SymbolEntry(path, i + 1, kind, name, superName, interfaces));
                currentType = name;
                int end = findBlockEnd(lines, i, MAX_TYPE_LINES);
                addChunk(path, i + 1, end + 1, name, kind, lines, i, end, chunks);
                continue;
            }

            if (currentType != null) {
                Matcher ctorMatcher = CONSTRUCTOR.matcher(line);
                if (ctorMatcher.find() && currentType.equals(ctorMatcher.group(1))) {
                    symbols.add(new WorkspaceIndex.SymbolEntry(path, i + 1, "constructor", ctorMatcher.group(1)));
                    int end = findBlockEnd(lines, i, MAX_METHOD_LINES);
                    addChunk(path, i + 1, end + 1, currentType + ".<init>", "constructor", lines, i, end, chunks);
                    continue;
                }
            }

            Matcher methodMatcher = METHOD_DEF.matcher(line);
            if (methodMatcher.find()) {
                String name = methodMatcher.group(1);
                if (!KEYWORDS.contains(name) && (currentType == null || !currentType.equals(name))) {
                    symbols.add(new WorkspaceIndex.SymbolEntry(path, i + 1, "method", name));
                    int end = findBlockEnd(lines, i, MAX_METHOD_LINES);
                    addChunk(path, i + 1, end + 1, name, "method", lines, i, end, chunks);
                }
            }
        }
    }

    private static List<String> splitTypes(String raw) {
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static int findBlockEnd(List<String> lines, int startLine, int maxSpan) {
        int depth = 0;
        boolean started = false;
        int limit = Math.min(lines.size() - 1, startLine + maxSpan);
        for (int i = startLine; i <= limit; i++) {
            String line = lines.get(i);
            for (int j = 0; j < line.length(); j++) {
                char c = line.charAt(j);
                if (c == '{') {
                    depth++;
                    started = true;
                } else if (c == '}') {
                    depth--;
                    if (started && depth <= 0) {
                        return i;
                    }
                }
            }
        }
        return limit;
    }

    private static void addChunk(
            String path,
            int startLine,
            int endLine,
            String symbolName,
            String kind,
            List<String> lines,
            int from,
            int to,
            List<WorkspaceIndex.CodeChunk> chunks) {
        StringBuilder text = new StringBuilder();
        for (int i = from; i <= to && i < lines.size(); i++) {
            text.append(lines.get(i)).append('\n');
            if (text.length() > MAX_CHUNK_CHARS) {
                break;
            }
        }
        String body = text.toString().trim();
        if (!body.isEmpty()) {
            chunks.add(new WorkspaceIndex.CodeChunk(path, startLine, endLine, symbolName, kind, body));
        }
    }
}
