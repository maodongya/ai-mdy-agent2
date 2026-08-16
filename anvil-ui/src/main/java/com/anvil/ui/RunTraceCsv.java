package com.anvil.ui;

import java.util.List;

/** CSV export for run trace rows (no JavaFX dependency). */
final class RunTraceCsv {

    private RunTraceCsv() {}

    static String export(List<RunTraceRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("seq,step,event,summary,metrics\n");
        for (RunTraceRow row : rows) {
            sb.append(row.seq())
                    .append(',')
                    .append(row.step())
                    .append(',')
                    .append(cell(row.type()))
                    .append(',')
                    .append(cell(row.summary()))
                    .append(',')
                    .append(cell(row.metrics()))
                    .append('\n');
        }
        return sb.toString();
    }

    static String cell(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
