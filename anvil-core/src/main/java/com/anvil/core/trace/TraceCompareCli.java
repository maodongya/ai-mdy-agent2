package com.anvil.core.trace;

import com.anvil.protocol.ProtocolJson;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** CLI entry for {@code scripts/compare-trace.sh}. */
public final class TraceCompareCli {

    private TraceCompareCli() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: TraceCompareCli <golden.json> <actual-trace.csv|event-list.txt>");
            System.exit(2);
        }
        Path goldenPath = Path.of(args[0]);
        Path actualPath = Path.of(args[1]);
        List<String> expected = loadGolden(goldenPath);
        List<String> actual = loadActual(actualPath);
        TraceComparator.TraceDiff diff = TraceComparator.compare(expected, actual);
        System.out.println(TraceComparator.formatReport(goldenPath.getFileName().toString(), diff));
        System.exit(diff.similarity() >= 0.8 ? 0 : 1);
    }

    private static List<String> loadGolden(Path path) throws Exception {
        JsonNode root = ProtocolJson.mapper().readTree(Files.readString(path));
        List<String> types = new ArrayList<>();
        for (JsonNode node : root.get("event_types")) {
            types.add(node.asText());
        }
        return types;
    }

    private static List<String> loadActual(Path path) throws Exception {
        String text = Files.readString(path);
        if (path.toString().endsWith(".csv")) {
            List<String> types = new ArrayList<>();
            for (String line : text.split("\n")) {
                if (line.isBlank() || line.startsWith("seq,")) {
                    continue;
                }
                int comma = line.indexOf(',');
                if (comma > 0) {
                    int second = line.indexOf(',', comma + 1);
                    if (second > comma) {
                        types.add(line.substring(comma + 1, second).trim());
                    }
                }
            }
            return types;
        }
        return List.of(text.split("\n"));
    }
}
