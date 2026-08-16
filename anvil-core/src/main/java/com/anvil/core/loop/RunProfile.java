package com.anvil.core.loop;

import com.anvil.core.compact.ContextBudget;
import com.anvil.protocol.Mode;

/** Presets for run depth: steps + context budget. */
public enum RunProfile {
    STANDARD(40, ContextBudget.standard()),
    EXTENDED(60, ContextBudget.extended()),
    COMPLEX(100, ContextBudget.complex());

    private final int defaultMaxSteps;
    private final ContextBudget contextBudget;

    RunProfile(int defaultMaxSteps, ContextBudget contextBudget) {
        this.defaultMaxSteps = defaultMaxSteps;
        this.contextBudget = contextBudget;
    }

    public int defaultMaxSteps() {
        return defaultMaxSteps;
    }

    public ContextBudget contextBudget() {
        return contextBudget;
    }

    public static RunProfile fromWire(String value) {
        if (value == null || value.isBlank()) {
            return STANDARD;
        }
        return switch (value.trim().toLowerCase()) {
            case "extended", "long" -> EXTENDED;
            case "complex", "deep" -> COMPLEX;
            default -> STANDARD;
        };
    }

    /** Phase 11: lean default — extended/complex require explicit selection. */
    public static RunProfile defaultFor(Mode mode) {
        return STANDARD;
    }
}
