package com.anvil.server.store;

import com.anvil.core.compact.ContextBudget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadMemoryStoreTest {

    @Test
    void persistsAcrossInstances(@TempDir Path temp) throws Exception {
        System.setProperty("anvil.memory.db-path", temp.resolve("mem.db").toString());
        MemoryDatabase db = new MemoryDatabase(temp.resolve("mem.db").toString());
        db.init();
        ThreadMemoryStore store = new ThreadMemoryStore(db);

        store.save("thr_x", List.of(Map.of("role", "user", "content", "hello")), ContextBudget.standard());

        ThreadMemoryStore reloaded = new ThreadMemoryStore(db);
        List<Map<String, Object>> history = reloaded.load("thr_x");

        assertEquals(1, history.size());
        assertTrue(String.valueOf(history.getFirst().get("content")).contains("hello"));
    }
}
