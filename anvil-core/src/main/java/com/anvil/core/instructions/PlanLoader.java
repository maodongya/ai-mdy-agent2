package com.anvil.core.instructions;

import com.anvil.tools.PlanTool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Loads persisted plan files for complex multi-step runs. */
public final class PlanLoader {

    private PlanLoader() {}

    public static Optional<String> loadPlan(Path workspaceRoot) {
        if (workspaceRoot == null) {
            return Optional.empty();
        }
        Path plan = workspaceRoot.resolve(PlanTool.PLAN_PATH);
        if (!Files.isRegularFile(plan)) {
            return Optional.empty();
        }
        try {
            String text = Files.readString(plan).trim();
            return text.isBlank() ? Optional.empty() : Optional.of(text);
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
