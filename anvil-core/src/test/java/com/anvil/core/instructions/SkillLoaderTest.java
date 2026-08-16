package com.anvil.core.instructions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillLoaderTest {

    @Test
    void loadsMatchingSkill(@TempDir Path workspace) throws Exception {
        Path dir = workspace.resolve(".anvil/skills");
        Files.createDirectories(dir);
        Files.writeString(
                dir.resolve("tdd.md"),
                """
                # skill: tdd
                Always write a failing test first.
                """);

        String block = SkillLoader.loadForRun(workspace, "please use tdd for this fix");

        assertTrue(block.contains("<skill name=\"tdd\">"));
        assertTrue(block.contains("failing test"));
    }

    @Test
    void showsIndexWhenNoMatch(@TempDir Path workspace) throws Exception {
        Path dir = workspace.resolve(".anvil/skills");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("refactor.md"), "# skill: refactor\nExtract methods.");

        String block = SkillLoader.loadForRun(workspace, "hello");

        assertTrue(block.contains("<skills_index>"));
        assertTrue(block.contains("refactor"));
    }
}
