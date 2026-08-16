package com.anvil.ui;

import com.anvil.protocol.Event;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Structured step-by-step view of an agent run (tokens, tools, compaction). */
final class RunDetailPanel extends StackPane {

    private static final int MAX_ROWS = 500;

    private final ObservableList<RunTraceRow> rows = FXCollections.observableArrayList();
    private final TableView<RunTraceRow> table = new TableView<>(rows);
    private final Label emptyHint = new Label("Run trace will appear here.\nSteps, tokens, and tool calls in table form.");
    private final List<RunTraceRow> pendingRows = new ArrayList<>();
    private long lastScrollAtMs;
    private boolean scrollPending;

    RunDetailPanel() {
        emptyHint.getStyleClass().add("empty-hint");
        emptyHint.setWrapText(true);

        TableColumn<RunTraceRow, Number> seqCol = new TableColumn<>("#");
        seqCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().seq()));
        seqCol.setPrefWidth(36);
        seqCol.setMaxWidth(48);

        TableColumn<RunTraceRow, Number> stepCol = new TableColumn<>("Step");
        stepCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().step()));
        stepCol.setPrefWidth(48);
        stepCol.setMaxWidth(56);

        TableColumn<RunTraceRow, String> typeCol = new TableColumn<>("Event");
        typeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().type()));
        typeCol.setPrefWidth(120);

        TableColumn<RunTraceRow, String> summaryCol = new TableColumn<>("Summary");
        summaryCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().summary()));
        summaryCol.setPrefWidth(280);

        TableColumn<RunTraceRow, String> metricsCol = new TableColumn<>("Metrics");
        metricsCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().metrics()));
        metricsCol.setPrefWidth(160);

        table.getColumns().addAll(seqCol, stepCol, typeCol, summaryCol, metricsCol);
        table.getStyleClass().add("run-trace-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label(""));
        rows.addListener((javafx.collections.ListChangeListener<? super RunTraceRow>) c -> updateEmpty());

        VBox box = new VBox(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        box.setPadding(new Insets(0));
        getChildren().addAll(box, emptyHint);
        updateEmpty();
    }

    void clear() {
        rows.clear();
        pendingRows.clear();
        scrollPending = false;
    }

    void onEvent(Event event) {
        if ("message.delta".equals(event.type())) {
            return;
        }
        Map<String, Object> payload = event.payload();
        pendingRows.add(new RunTraceRow(
                event.seq(),
                stepFrom(payload),
                event.type(),
                AgentEventFormatter.format(event.type(), payload),
                metricsFrom(event.type(), payload)));
    }

    void flushPendingRows() {
        if (pendingRows.isEmpty()) {
            return;
        }
        rows.addAll(pendingRows);
        pendingRows.clear();
        while (rows.size() > MAX_ROWS) {
            rows.remove(0);
        }
        scheduleScrollToEnd();
    }

    private static int stepFrom(Map<String, Object> payload) {
        Object step = payload.get("step");
        if (step instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private static String metricsFrom(String type, Map<String, Object> payload) {
        return switch (type) {
            case "step.started" -> "~" + num(payload.get("context_tokens_estimate")) + " tok · "
                    + num(payload.get("context_messages")) + " msgs";
            case "model.completed" -> "in=" + num(payload.get("input_tokens"))
                    + " out=" + num(payload.get("output_tokens"))
                    + " · " + num(payload.get("latency_ms")) + "ms";
            case "context.compacted" -> num(payload.get("before_tokens")) + " → " + num(payload.get("after_tokens"));
            case "run.completed", "run.failed", "run.cancelled" -> AgentEventFormatter.metricsSummary(usage(payload));
            case "thread.memory.loaded" -> num(payload.get("messages")) + " msgs · ~" + num(payload.get("tokens_estimate"));
            default -> "";
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> usage(Map<String, Object> payload) {
        Object usage = payload.get("usage");
        if (usage instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        Object total = payload.get("usage_total");
        if (total instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String num(Object value) {
        return value == null ? "0" : String.valueOf(value);
    }

    boolean isEmpty() {
        return rows.isEmpty() && pendingRows.isEmpty();
    }

    String exportCsv() {
        flushPendingRows();
        return RunTraceCsv.export(List.copyOf(rows));
    }

    private void scheduleScrollToEnd() {
        long now = System.currentTimeMillis();
        if (now - lastScrollAtMs < 250) {
            if (!scrollPending) {
                scrollPending = true;
                javafx.application.Platform.runLater(() -> {
                    scrollPending = false;
                    scrollToEnd();
                });
            }
            return;
        }
        scrollToEnd();
    }

    private void scrollToEnd() {
        if (rows.isEmpty()) {
            return;
        }
        lastScrollAtMs = System.currentTimeMillis();
        table.scrollTo(rows.size() - 1);
    }

    private void updateEmpty() {
        emptyHint.setVisible(rows.isEmpty());
        emptyHint.setManaged(rows.isEmpty());
        table.setVisible(!rows.isEmpty());
    }
}
