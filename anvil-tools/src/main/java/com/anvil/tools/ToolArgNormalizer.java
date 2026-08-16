package com.anvil.tools;

import java.util.LinkedHashMap;
import java.util.Map;

/** Normalizes common LLM argument aliases before tool dispatch. */
public final class ToolArgNormalizer {

    private ToolArgNormalizer() {}

    public static Map<String, Object> normalize(String toolName, Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>(args);
        alias(out, "path", "file_path", "filePath", "filepath", "file");
        alias(out, "content", "text", "body", "data");
        alias(out, "pattern", "glob", "glob_pattern");
        alias(out, "command", "cmd", "shell");
        alias(out, "old_string", "oldString", "old_text", "search");
        alias(out, "new_string", "newString", "new_text", "replace");
        alias(out, "query", "q", "search_query");
        alias(out, "path_glob", "glob_filter", "include");
        if ("fs.write".equals(toolName) && !out.containsKey("path") && out.containsKey("filename")) {
            out.put("path", out.remove("filename"));
        }
        return out;
    }

    private static void alias(Map<String, Object> args, String canonical, String... aliases) {
        if (args.containsKey(canonical)) {
            return;
        }
        for (String alias : aliases) {
            if (args.containsKey(alias)) {
                args.put(canonical, args.remove(alias));
                return;
            }
        }
    }
}
