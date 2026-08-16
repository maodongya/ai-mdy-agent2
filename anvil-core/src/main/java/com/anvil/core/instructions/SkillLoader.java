package com.anvil.core.instructions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Loads `.anvil/skills/*.md` and injects matching skills (Phase 5). */
public final class SkillLoader {

    private static final String SKILLS_DIR = ".anvil/skills";

    private SkillLoader() {}

    public record Skill(String name, String path, String body) {}

    public static String loadForRun(Path workspaceRoot, String userMessage) {
        List<Skill> skills = listSkills(workspaceRoot);
        if (skills.isEmpty()) {
            return "";
        }
        String msg = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        List<Skill> matched = new ArrayList<>();
        for (Skill skill : skills) {
            if (msg.contains(skill.name().toLowerCase(Locale.ROOT))) {
                matched.add(skill);
            }
        }
        if (matched.isEmpty()) {
            return formatIndex(skills);
        }
        return formatSkills(matched);
    }

    static List<Skill> listSkills(Path workspaceRoot) {
        Path dir = workspaceRoot.resolve(SKILLS_DIR);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Skill> skills = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> parseSkill(p).ifPresent(skills::add));
        } catch (IOException ignored) {
            return List.of();
        }
        return List.copyOf(skills);
    }

    private static java.util.Optional<Skill> parseSkill(Path file) {
        try {
            String raw = Files.readString(file);
            String name = file.getFileName().toString().replace(".md", "");
            String body = raw.trim();
            for (String line : raw.split("\n", 3)) {
                String trimmed = line.trim();
                if (trimmed.toLowerCase(Locale.ROOT).startsWith("# skill:")) {
                    name = trimmed.substring(8).trim();
                    break;
                }
            }
            return java.util.Optional.of(new Skill(name, workspaceRel(file), body));
        } catch (IOException e) {
            return java.util.Optional.empty();
        }
    }

    private static String workspaceRel(Path file) {
        return file.getFileName().toString();
    }

    private static String formatIndex(List<Skill> skills) {
        StringBuilder sb = new StringBuilder("<skills_index>\n");
        sb.append("Available skills (mention by name to activate):\n");
        for (Skill skill : skills) {
            sb.append("- ").append(skill.name()).append('\n');
        }
        sb.append("</skills_index>");
        return sb.toString();
    }

    private static String formatSkills(List<Skill> skills) {
        StringBuilder sb = new StringBuilder();
        for (Skill skill : skills) {
            sb.append("<skill name=\"")
                    .append(skill.name())
                    .append("\">\n")
                    .append(skill.body())
                    .append("\n</skill>\n\n");
        }
        return sb.toString().trim();
    }
}
