package com.anvil.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Cursor-style Problems panel for compile diagnostics (Phase 9.3). */
final class ProblemsPanel extends VBox {

    private final Label header = new Label("PROBLEMS");
    private final ListView<ProblemRow> list = new ListView<>();
    private Consumer<ProblemRow> onOpen;
    private final ObservableList<ProblemRow> rows = FXCollections.observableArrayList();

    ProblemsPanel() {
        super(4);
        getStyleClass().add("problems-panel");
        header.getStyleClass().add("section-label");
        list.setItems(rows);
        list.setPrefHeight(120);
        list.setMaxHeight(180);
        list.getStyleClass().add("problems-list");
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ProblemRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(item.label());
                getStyleClass().removeAll("problem-error", "problem-warning");
                getStyleClass().add("ERROR".equalsIgnoreCase(item.severity()) ? "problem-error" : "problem-warning");
            }
        });
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() >= 2) {
                ProblemRow row = list.getSelectionModel().getSelectedItem();
                if (row != null && onOpen != null) {
                    onOpen.accept(row);
                }
            }
        });
        VBox.setVgrow(list, Priority.SOMETIMES);
        setPadding(new Insets(6, 8, 6, 8));
        getChildren().addAll(header, list);
        setVisible(false);
        setManaged(false);
    }

    void setOnOpen(Consumer<ProblemRow> handler) {
        this.onOpen = handler;
    }

    void setProblems(List<ProblemRow> problems) {
        rows.setAll(problems);
        boolean empty = problems == null || problems.isEmpty();
        setVisible(!empty);
        setManaged(!empty);
        header.setText(empty ? "PROBLEMS" : "PROBLEMS (" + problems.size() + ")");
    }

    void clear() {
        setProblems(List.of());
    }

    static List<ProblemRow> fromPayload(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(m -> new ProblemRow(
                        String.valueOf(m.getOrDefault("path", "")),
                        intVal(m.get("line"), 1),
                        intVal(m.get("column"), 0),
                        String.valueOf(m.getOrDefault("severity", "ERROR")),
                        String.valueOf(m.getOrDefault("message", ""))))
                .filter(r -> !r.path().isBlank())
                .toList();
    }

    private static int intVal(Object v, int fallback) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return v == null ? fallback : Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    record ProblemRow(String path, int line, int column, String severity, String message) {
        String label() {
            String file = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            return severity + " " + file + ":" + line + " — " + message;
        }
    }
}
