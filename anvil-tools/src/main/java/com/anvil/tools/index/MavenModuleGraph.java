package com.anvil.tools.index;

import com.anvil.tools.WorkspaceWalk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maven multi-module dependency graph for Run harness context (Phase 6.3). */
public final class MavenModuleGraph {

    private static final Pattern ARTIFACT_ID = Pattern.compile("<artifactId>([^<]+)</artifactId>");
    private static final Pattern MODULE = Pattern.compile("<module>([^<]+)</module>");
    private static final Pattern DEP_ARTIFACT = Pattern.compile(
            "<dependency>\\s*<groupId>[^<]+</groupId>\\s*<artifactId>([^<]+)</artifactId>",
            Pattern.DOTALL);

    private MavenModuleGraph() {}

    public record ModuleInfo(
            String path,
            String artifactId,
            List<String> childModules,
            List<String> dependencies) {}

    public static List<ModuleInfo> scan(Path workspaceRoot) {
        Path root = workspaceRoot.toAbsolutePath().normalize();
        List<ModuleInfo> modules = new ArrayList<>();
        try {
            WorkspaceWalk.forEachFile(root, file -> {
                String rel = root.relativize(file).toString().replace('\\', '/');
                if (!"pom.xml".equals(rel) && !rel.endsWith("/pom.xml")) {
                    return;
                }
                try {
                    String xml = Files.readString(file, StandardCharsets.UTF_8);
                    modules.add(parseModule(rel, xml));
                } catch (IOException ignored) {
                    // skip
                }
            });
        } catch (IOException ignored) {
            return List.of();
        }
        modules.sort(Comparator.comparing(ModuleInfo::path));
        return modules;
    }

    public static String format(Path workspaceRoot) {
        List<ModuleInfo> modules = scan(workspaceRoot);
        if (modules.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("<module_graph>\n");
        for (ModuleInfo m : modules) {
            sb.append("- ").append(m.path()).append(" (").append(m.artifactId()).append(')');
            if (!m.childModules().isEmpty()) {
                sb.append(" modules: ").append(String.join(", ", m.childModules()));
            }
            if (!m.dependencies().isEmpty()) {
                sb.append(" deps: ").append(String.join(", ", m.dependencies()));
            }
            sb.append('\n');
        }
        sb.append("</module_graph>");
        return sb.toString();
    }

    private static ModuleInfo parseModule(String path, String xml) {
        String artifactId = firstMatch(ARTIFACT_ID, xml, "unknown");
        List<String> childModules = allMatches(MODULE, xml);
        LinkedHashSet<String> deps = new LinkedHashSet<>();
        Matcher depMatcher = DEP_ARTIFACT.matcher(xml);
        while (depMatcher.find() && deps.size() < 12) {
            deps.add(depMatcher.group(1).trim());
        }
        return new ModuleInfo(path, artifactId, childModules, List.copyOf(deps));
    }

    private static String firstMatch(Pattern pattern, String text, String fallback) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : fallback;
    }

    private static List<String> allMatches(Pattern pattern, String text) {
        List<String> out = new ArrayList<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            out.add(m.group(1).trim());
        }
        return out;
    }
}
