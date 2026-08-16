package com.anvil.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanToolTest {

    @TempDir
    Path workspace;

    @Test
    void writesPlanMarkdown() throws Exception {
        FsTools fs = new FsTools(workspace);
        var result = PlanTool.update("tc1", fs, "# Tasks\n- item");
        assertEquals("ok", result.status());
        Path plan = workspace.resolve(PlanTool.PLAN_PATH);
        assertTrue(Files.exists(plan));
        assertTrue(Files.readString(plan).contains("# Tasks"));
    }
}
