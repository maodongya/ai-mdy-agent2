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

    /** Phase 11.4: compact plan steps for history after plan.update. */
    public static String formatStepsSummary(List<String> steps) {
        if (steps.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("<plan_summary path=\"" + PlanTool.PLAN_PATH + "\">\n");
        int i = 1;
        for (String step : steps) {
            String line = i + ". " + step;
            if (sb.length() + line.length() > 480) {
                sb.append("… ").append(steps.size() - i + 1).append(" more steps\n");
                break;
            }
            sb.append(line).append('\n');
            i++;
        }
        sb.append("</plan_summary>");
        return sb.toString().trim();
    }

    /** Phase 11.4: inject summarized plan at run start when file is large. */
    public static String formatActivePlanBlock(String planMarkdown) {
        if (planMarkdown == null || planMarkdown.isBlank()) {
            return "";
        }
        if (planMarkdown.length() <= 500) {
            return "<active_plan path=\"" + PlanTool.PLAN_PATH + "\">\n" + planMarkdown.trim() + "\n</active_plan>";
        }
        List<String> steps = steps(planMarkdown);
        if (!steps.isEmpty()) {
            return formatStepsSummary(steps);
        }
        return "<active_plan_summary path=\""
                + PlanTool.PLAN_PATH
                + "\">\n"
                + planMarkdown.substring(0, Math.min(480, planMarkdown.length())).trim()
                + "\n… [use fs.read on "
                + PlanTool.PLAN_PATH
                + " for full plan]\n</active_plan_summary>";
    }
}
