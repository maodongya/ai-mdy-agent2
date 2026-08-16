package com.anvil.core.orchestrator;

import com.anvil.tools.PlanTool;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses structured steps from `.anvil/plan.md` (Phase 10.2). */
public final class PlanParser {

    private static final Pattern CHECKBOX = Pattern.compile("^\\s*[-*]\\s*\\[[ xX]?\\]\\s+(.+)$");
    private static final Pattern NUMBERED = Pattern.compile("^\\s*\\d+[.)]\\s+(.+)$");

    private PlanParser() {}

    public static List<String> steps(String planMarkdown) {
        if (planMarkdown == null || planMarkdown.isBlank()) {
            return List.of();
        }
        List<String> steps = new ArrayList<>();
        for (String line : planMarkdown.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Matcher cb = CHECKBOX.matcher(line);
            if (cb.find()) {
                steps.add(cb.group(1).trim());
                continue;
            }
            Matcher num = NUMBERED.matcher(line);
            if (num.find()) {
                steps.add(num.group(1).trim());
            }
        }
        return List.copyOf(steps);
    }

    public static String formatStepsBlock(List<String> steps) {
        if (steps.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("<plan_steps path=\"" + PlanTool.PLAN_PATH + "\">\n");
        int i = 1;
        for (String step : steps) {
            sb.append(i++).append(". ").append(step).append('\n');
        }
        sb.append("</plan_steps>");
        return sb.toString();
    }
}
