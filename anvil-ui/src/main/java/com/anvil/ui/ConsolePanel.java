package com.anvil.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

/** Colored, auto-scrolling agent console with empty state. */
final class ConsolePanel extends StackPane {

    private static final int MAX_LINES = 250;
    private static final int STREAM_MAX_DISPLAY_CHARS = 32_000;

    private final ObservableList<ConsoleLine> lines = FXCollections.observableArrayList();
    private final ListView<ConsoleLine> list = new ListView<>(lines);
    private final TextArea streamingArea = new TextArea();
    private final javafx.scene.control.Label emptyHint =
            new javafx.scene.control.Label("Agent output will appear here.\nRun a prompt with ⌘↩");
    private final StringBuilder streamBuffer = new StringBuilder();
    private boolean streamingActive;
    private long lastScrollMs;

    ConsolePanel() {
        emptyHint.getStyleClass().add("empty-hint");
        emptyHint.setWrapText(true);

        Font mono = Font.font("Monospaced", 12);
        streamingArea.setEditable(false);
        streamingArea.setWrapText(true);
        streamingArea.setFocusTraversable(false);
        streamingArea.setFont(mono);
        streamingArea.setPrefRowCount(3);
        streamingArea.setMaxHeight(140);
        streamingArea.getStyleClass().addAll("console-streaming", "text-area");
        streamingArea.setVisible(false);
        streamingArea.setManaged(false);

        list.getStyleClass().add("console-list");
        list.setCellFactory(lv -> new ConsoleCell());
        list.setFocusTraversable(false);
        lines.addListener((javafx.collections.ListChangeListener<? super ConsoleLine>) c -> updateEmpty());

        VBox box = new VBox(4, list, streamingArea);
        VBox.setVgrow(list, Priority.ALWAYS);
        getChildren().addAll(box, emptyHint);
        updateEmpty();
    }

    void append(ConsoleLine.Kind kind, String text) {
        finishStreaming();
        lines.add(ConsoleLine.of(kind, text));
        trimLines();
        maybeScrollToEnd();
    }

    void appendDelta(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        if (!streamingActive) {
            streamingActive = true;
            streamBuffer.setLength(0);
            streamingArea.clear();
            streamingArea.setVisible(true);
            streamingArea.setManaged(true);
        }
        streamBuffer.append(delta);
        if (streamBuffer.length() <= STREAM_MAX_DISPLAY_CHARS) {
            streamingArea.appendText(delta);
        } else {
            streamingArea.setText("…" + streamBuffer.substring(streamBuffer.length() - STREAM_MAX_DISPLAY_CHARS));
            streamingArea.setScrollTop(Double.MAX_VALUE);
        }
        updateEmpty();
    }

    void finishStreaming() {
        if (!streamingActive) {
            return;
        }
        String text = streamBuffer.toString();
        streamBuffer.setLength(0);
        streamingArea.clear();
        streamingArea.setVisible(false);
        streamingArea.setManaged(false);
        streamingActive = false;
        if (!text.isBlank()) {
            lines.add(ConsoleLine.of(ConsoleLine.Kind.MESSAGE, text));
            trimLines();
            maybeScrollToEnd();
        }
        updateEmpty();
    }

    boolean isStreaming() {
        return streamingActive;
    }

    void clear() {
        streamingActive = false;
        streamBuffer.setLength(0);
        streamingArea.clear();
        streamingArea.setVisible(false);
        streamingArea.setManaged(false);
        lines.clear();
    }

    String copyAllText() {
        StringBuilder sb = new StringBuilder();
        for (ConsoleLine line : lines) {
            sb.append('[').append(line.formattedTime()).append("] ");
            sb.append(formatLine(line)).append('\n');
        }
        if (streamingActive && !streamBuffer.isEmpty()) {
            sb.append("[streaming] ").append(streamBuffer).append('\n');
        }
        return sb.toString();
    }

    boolean isEmpty() {
        return lines.isEmpty() && !streamingActive;
    }

    private void trimLines() {
        while (lines.size() > MAX_LINES) {
            lines.remove(0);
        }
    }

    private void maybeScrollToEnd() {
        if (lines.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastScrollMs < 200) {
            return;
        }
        lastScrollMs = now;
        list.scrollTo(lines.size() - 1);
    }

    private void updateEmpty() {
        boolean empty = lines.isEmpty() && !streamingActive;
        emptyHint.setVisible(empty);
        emptyHint.setManaged(empty);
        list.setVisible(!empty || streamingActive);
    }

    private static String formatLine(ConsoleLine line) {
        return switch (line.kind()) {
            case USER -> "> " + line.text();
            case MESSAGE -> line.text();
            case TOOL -> "⚙ " + line.text();
            case APPROVAL -> "✓ " + line.text();
            case ERROR -> "✗ " + line.text();
            case SYSTEM -> "· " + line.text();
            case CONTEXT -> "◆ " + line.text();
            case METRICS -> "▲ " + line.text();
        };
    }

    private static final class ConsoleCell extends ListCell<ConsoleLine> {
        private ConsoleLine.Kind lastKind;

        @Override
        protected void updateItem(ConsoleLine item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                clearStyle();
                lastKind = null;
                return;
            }
            if (item.kind() != lastKind) {
                clearStyle();
                getStyleClass().add(styleClassFor(item.kind()));
                lastKind = item.kind();
            }
            setText("[" + item.formattedTime() + "] " + formatLine(item));
        }

        private void clearStyle() {
            getStyleClass().removeAll(
                    "console-user",
                    "console-message",
                    "console-tool",
                    "console-approval",
                    "console-error",
                    "console-system",
                    "console-context",
                    "console-metrics");
        }

        private static String styleClassFor(ConsoleLine.Kind kind) {
            return switch (kind) {
                case USER -> "console-user";
                case MESSAGE -> "console-message";
                case TOOL -> "console-tool";
                case APPROVAL -> "console-approval";
                case ERROR -> "console-error";
                case SYSTEM -> "console-system";
                case CONTEXT -> "console-context";
                case METRICS -> "console-metrics";
            };
        }
    }
}
