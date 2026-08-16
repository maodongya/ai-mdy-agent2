package com.anvil.tools.index;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Rule-based query expansion for codebase search (Phase 6.5). */
public final class QueryExpander {

    private static final Pattern CAMEL = Pattern.compile("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");

    private QueryExpander() {}

    /** Returns original query plus camel/snake/token variants (max 6). */
    public static List<String> expand(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        String q = query.trim();
        out.add(q);

        String lower = q.toLowerCase(Locale.ROOT);
        out.add(lower);

        String snake = toSnake(q);
        if (!snake.isBlank()) {
            out.add(snake);
            out.add(snake.replace('_', ' '));
        }

        String camelFlat = q.replaceAll("[\\s_\\-]+", "");
        if (!camelFlat.isBlank()) {
            out.add(camelFlat);
        }

        for (String token : q.split("[\\s_/\\-]+")) {
            if (token.length() >= 3) {
                out.add(token);
                out.add(token.toLowerCase(Locale.ROOT));
            }
        }

        String[] camelParts = CAMEL.split(q.replaceAll("[\\s_\\-]+", ""));
        for (String part : camelParts) {
            if (part.length() >= 3) {
                out.add(part);
                out.add(part.toLowerCase(Locale.ROOT));
            }
        }

        // Coding synonyms (lightweight)
        addSynonyms(out, lower);

        List<String> list = new ArrayList<>(out);
        return list.size() > 6 ? list.subList(0, 6) : list;
    }

    private static void addSynonyms(Set<String> out, String lower) {
        if (lower.contains("connect") || lower.contains("connection")) {
            out.add("connect");
            out.add("Client");
        }
        if (lower.contains("config") || lower.contains("configuration")) {
            out.add("Config");
            out.add("application.yml");
        }
        if (lower.contains("test")) {
            out.add("Test");
            out.add("junit");
        }
        if (lower.contains("controller") || lower.contains("api")) {
            out.add("Controller");
            out.add("RestController");
        }
    }

    static String toSnake(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String s = input.trim().replace('-', '_').replace(' ', '_');
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c) && i > 0 && s.charAt(i - 1) != '_') {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString().replaceAll("_+", "_");
    }
}
