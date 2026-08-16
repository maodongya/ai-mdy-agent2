package com.anvil.core.compact;

import java.util.LinkedHashMap;
import java.util.Map;

/** Run-scoped dedup for fs.read results (Phase 11.3). */
public final class ReadCache {

    private final Map<String, CachedRead> entries = new LinkedHashMap<>();

    public record CachedRead(String toolCallId, String content) {}

    public CachedRead get(String key) {
        return entries.get(key);
    }

    public void put(String key, String toolCallId, String content) {
        entries.put(key, new CachedRead(toolCallId, content));
    }

    public static String cacheKey(String path, Integer offset, Integer limit) {
        return path + "@" + (offset == null ? 1 : offset) + ":" + (limit == null ? -1 : limit);
    }
}
