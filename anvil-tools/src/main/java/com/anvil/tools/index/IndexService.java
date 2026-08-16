package com.anvil.tools.index;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/** Cached workspace index with disk persistence. */
public final class IndexService {

    private static final ConcurrentHashMap<String, WorkspaceIndex> CACHE = new ConcurrentHashMap<>();

    private IndexService() {}

    public static WorkspaceIndex get(Path workspaceRoot) {
        Path root = workspaceRoot.toAbsolutePath().normalize();
        String key = root.toString();
        return CACHE.compute(key, (k, existing) -> {
            if (existing != null && !IndexStore.isStale(root, existing)) {
                return existing;
            }
            WorkspaceIndex loaded = IndexStore.load(root);
            if (loaded != null && !IndexStore.isStale(root, loaded)) {
                return loaded;
            }
            try {
                WorkspaceIndex built = IndexBuilder.build(root);
                IndexStore.save(root, built);
                return built;
            } catch (IOException e) {
                return loaded != null ? loaded : WorkspaceIndex.empty();
            }
        });
    }

    public static void invalidate(Path workspaceRoot) {
        CACHE.remove(workspaceRoot.toAbsolutePath().normalize().toString());
    }

    public static void warm(Path workspaceRoot) {
        get(workspaceRoot);
    }
}
