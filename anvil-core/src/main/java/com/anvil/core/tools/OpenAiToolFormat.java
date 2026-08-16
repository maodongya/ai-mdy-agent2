package com.anvil.core.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Normalizes tool definitions for OpenAI Chat Completions API. */
public final class OpenAiToolFormat {

    private OpenAiToolFormat() {}

    public record NormalizedTools(List<Map<String, Object>> tools, Map<String, String> apiToCanonical) {}

    public static List<Map<String, Object>> normalize(List<Map<String, Object>> tools) {
        return normalize(tools, false).tools();
    }

    /** @param strictApiNames when true, map {@code fs.read} → {@code fs_read} for providers like DeepSeek */
    public static NormalizedTools normalize(List<Map<String, Object>> tools, boolean strictApiNames) {
        if (tools == null || tools.isEmpty()) {
            return new NormalizedTools(List.of(), Map.of());
        }
        Map<String, String> apiToCanonical = new LinkedHashMap<>();
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> tool : tools) {
            Map<String, Object> one = normalizeOne(tool, strictApiNames, apiToCanonical);
            normalized.add(one);
        }
        return new NormalizedTools(List.copyOf(normalized), Map.copyOf(apiToCanonical));
    }

    public static String toApiName(String canonical) {
        return canonical.replace('.', '_');
    }

    public static String resolveCanonicalName(String apiName, Map<String, String> apiToCanonical) {
        if (apiName == null) {
            return null;
        }
        return apiToCanonical.getOrDefault(apiName, apiName);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeOne(
            Map<String, Object> tool, boolean strictApiNames, Map<String, String> apiToCanonical) {
        String canonical;
        Map<String, Object> function;
        if (tool.containsKey("function") && tool.get("function") instanceof Map<?, ?> fn) {
            function = new LinkedHashMap<>((Map<String, Object>) fn);
            canonical = String.valueOf(function.get("name"));
        } else {
            canonical = String.valueOf(tool.get("name"));
            function = new LinkedHashMap<>();
            function.put("description", tool.getOrDefault("description", ""));
            function.put(
                    "parameters",
                    tool.getOrDefault("parameters", Map.of("type", "object", "properties", Map.of())));
        }
        String apiName = strictApiNames ? toApiName(canonical) : canonical;
        apiToCanonical.put(apiName, canonical);
        function.put("name", apiName);
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("type", "function");
        wrapped.put("function", function);
        return wrapped;
    }
}
