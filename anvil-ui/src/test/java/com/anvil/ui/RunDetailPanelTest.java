package com.anvil.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunDetailPanelTest {

    @Test
    void exportCsvEscapesCommasAndQuotes() {
        String csv = RunTraceCsv.export(List.of(new RunTraceRow(
                1, 2, "tool.planned", "plan · fs.read {\"path\":\"a,b\"}", "")));
        assertTrue(csv.startsWith("seq,step,event,summary,metrics\n"));
        assertTrue(csv.contains("tool.planned"));
        assertTrue(csv.contains("\"plan · fs.read"));
    }

    @Test
    void cellQuotesEmbeddedNewlines() {
        assertEquals("\"a\nb\"", RunTraceCsv.cell("a\nb"));
    }
}
