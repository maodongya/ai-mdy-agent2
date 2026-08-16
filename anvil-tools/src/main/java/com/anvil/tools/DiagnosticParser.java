package com.anvil.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses javac / Maven compiler output into structured diagnostics. */
public final class DiagnosticParser {

    public record Diagnostic(String file, int line, int column, String severity, String message) {}

    private static final Pattern MAVEN =
            Pattern.compile("\\[(ERROR|WARNING)]\\s+([^:\\[]+):\\[(\\d+),(\\d+)]\\s*(.*)");
    private static final Pattern JAVAC = Pattern.compile("^([^:]+):(\\d+):\\s*(?:error:|warning:)?\\s*(.*)$", Pattern.MULTILINE);
    private static final Pattern SIMPLE = Pattern.compile("([^\\s]+\\.java):(\\d+).*?(error|ERROR|cannot find symbol|';' expected)", Pattern.CASE_INSENSITIVE);

    private DiagnosticParser() {}

    public static List<Diagnostic> parse(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }
        List<Diagnostic> found = new ArrayList<>();
        Map<String, Diagnostic> dedupe = new LinkedHashMap<>();
        for (String line : output.split("\n")) {
            Diagnostic d = parseLine(line.trim());
            if (d != null) {
                dedupe.putIfAbsent(key(d), d);
            }
        }
        found.addAll(dedupe.values());
        if (found.isEmpty()) {
            Matcher m = SIMPLE.matcher(output);
            while (m.find()) {
                Diagnostic d = new Diagnostic(m.group(1), Integer.parseInt(m.group(2)), 0, "ERROR", m.group(0));
                dedupe.putIfAbsent(key(d), d);
            }
            found.addAll(dedupe.values());
        }
        return List.copyOf(found);
    }

    public static String format(List<Diagnostic> diagnostics, int maxItems) {
        if (diagnostics.isEmpty()) {
            return "no diagnostics parsed";
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Diagnostic d : diagnostics) {
            if (n >= maxItems) {
                sb.append("... and ").append(diagnostics.size() - maxItems).append(" more\n");
                break;
            }
            sb.append(d.severity())
                    .append(' ')
                    .append(d.file())
                    .append(':')
                    .append(d.line());
            if (d.column() > 0) {
                sb.append(':').append(d.column());
            }
            sb.append(' ').append(d.message()).append('\n');
            n++;
        }
        return sb.toString().trim();
    }

    private static Diagnostic parseLine(String line) {
        Matcher maven = MAVEN.matcher(line);
        if (maven.find()) {
            return new Diagnostic(
                    maven.group(2).trim(),
                    Integer.parseInt(maven.group(3)),
                    Integer.parseInt(maven.group(4)),
                    maven.group(1),
                    maven.group(5).trim());
        }
        Matcher javac = JAVAC.matcher(line);
        if (javac.find()) {
            return new Diagnostic(
                    javac.group(1).trim(),
                    Integer.parseInt(javac.group(2)),
                    0,
                    line.toLowerCase().contains("warning") ? "WARNING" : "ERROR",
                    javac.group(3).trim());
        }
        return null;
    }

    private static String key(Diagnostic d) {
        return d.file() + ":" + d.line() + ":" + d.column() + ":" + d.message();
    }
}
