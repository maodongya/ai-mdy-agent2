package com.anvil.tools.index;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses {@code @path} / {@code @symbol} references from user prompts (Phase 6.4). */
public final class AtReferenceParser {

    private static final Pattern AT = Pattern.compile("@([\\w./\\-]+(?:\\.[\\w]+)?)");

    private AtReferenceParser() {}

    public record Result(String cleanedMessage, List<String> resolvedPaths) {}

    public static Result parse(String message, Path workspaceRoot) {
        if (message == null || message.isBlank()) {
            return new Result("", List.of());
        }
        Path root = workspaceRoot.toAbsolutePath().normalize();
        WorkspaceIndex index = IndexService.get(root);
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        Matcher matcher = AT.matcher(message);
        StringBuffer cleaned = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1);
            String path = resolveToken(token, root, index);
            if (path != null) {
                resolved.add(path);
            }
            matcher.appendReplacement(cleaned, " ");
        }
        matcher.appendTail(cleaned);
        String msg = cleaned.toString().replaceAll("\\s+", " ").trim();
        return new Result(msg, List.copyOf(resolved));
    }

    private static String resolveToken(String token, Path root, WorkspaceIndex index) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String normalized = token.replace('\\', '/').trim();

        Path direct = root.resolve(normalized);
        if (Files.isRegularFile(direct)) {
            return root.relativize(direct).toString().replace('\\', '/');
        }
        if (Files.isDirectory(direct)) {
            return root.relativize(direct).toString().replace('\\', '/');
        }

        for (String path : index.paths()) {
            if (path.equals(normalized) || path.endsWith("/" + normalized) || path.endsWith(normalized)) {
                return path;
            }
        }

        String fileName = normalized.contains("/")
                ? normalized.substring(normalized.lastIndexOf('/') + 1)
                : normalized;
        for (String path : index.paths()) {
            if (path.endsWith("/" + fileName) || path.equals(fileName)) {
                return path;
            }
        }

        String symbol = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        for (WorkspaceIndex.SymbolEntry sym : index.symbols()) {
            if (sym.name().equalsIgnoreCase(symbol)) {
                return sym.path();
            }
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        for (String path : index.paths()) {
            if (path.toLowerCase(Locale.ROOT).contains(lower)) {
                return path;
            }
        }
        return null;
    }
}
