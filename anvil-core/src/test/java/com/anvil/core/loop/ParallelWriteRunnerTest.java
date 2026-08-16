package com.anvil.core.loop;

import com.anvil.core.model.ToolCallIntent;
import com.anvil.core.policy.Decision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParallelWriteRunnerTest {

    @Test
    void batchParallelizableWhenDistinctPaths() {
        var a = new ToolCallIntent("t1", "search_replace", Map.of("path", "A.java"));
        var b = new ToolCallIntent("t2", "search_replace", Map.of("path", "B.java"));
        assertTrue(ParallelWriteRunner.batchParallelizable(List.of(a, b)));
    }

    @Test
    void rejectsSamePathBatch() {
        var a = new ToolCallIntent("t1", "fs.write", Map.of("path", "A.java"));
        var b = new ToolCallIntent("t2", "search_replace", Map.of("path", "A.java"));
        assertFalse(ParallelWriteRunner.batchParallelizable(List.of(a, b)));
    }

    @Test
    void canParallelizeWriteTools() {
        assertTrue(ParallelWriteRunner.canParallelize("apply_patch", Decision.allow(), true));
        assertFalse(ParallelWriteRunner.canParallelize("grep", Decision.allow(), true));
    }
}
