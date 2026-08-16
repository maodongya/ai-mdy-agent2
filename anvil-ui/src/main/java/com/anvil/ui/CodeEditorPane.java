package com.anvil.ui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;

/**
 * 只读代码查看器。
 * <p>
 * 左侧行号侧栏（VBox + Label）通过 {@link TextArea#scrollTopProperty()} 与代码区垂直同步，
 * 侧栏无滚动条，宽度固定。
 */
final class CodeEditorPane extends BorderPane {

    private final TextArea code = new TextArea();
    private final VBox gutter = new VBox();
    private final StackPane gutterHost = new StackPane();
    private int findAnchor;
    private double lineHeight = 15;

    CodeEditorPane() {
        super();
        getStyleClass().add("code-editor");
        setMinSize(0, 0);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        Font mono = Font.font("Monospaced", 12);

        code.setEditable(false);
        code.setWrapText(false);
        code.setFont(mono);
        code.getStyleClass().add("text-area");
        code.setPrefRowCount(1);
        code.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        gutter.getStyleClass().add("code-gutter");
        gutter.setAlignment(Pos.TOP_RIGHT);
        gutter.setFillWidth(true);
        gutter.setMouseTransparent(true);
        gutter.setFocusTraversable(false);
        gutter.translateYProperty().bind(Bindings.createDoubleBinding(
                () -> -code.getScrollTop(), code.scrollTopProperty()));

        Rectangle clip = new Rectangle();
        gutterHost.getChildren().add(gutter);
        gutterHost.setAlignment(Pos.TOP_LEFT);
        gutterHost.setClip(clip);
        clip.widthProperty().bind(gutterHost.widthProperty());
        clip.heightProperty().bind(gutterHost.heightProperty());
        gutterHost.getStyleClass().add("code-gutter-host");
        gutterHost.setMinWidth(40);
        gutterHost.setPrefWidth(40);
        gutterHost.setMaxWidth(48);

        setLeft(gutterHost);
        setCenter(code);

        gutterHost.addEventFilter(ScrollEvent.SCROLL, this::forwardScrollToCode);
        code.textProperty().addListener((obs, o, n) -> refreshGutter());
        code.skinProperty().addListener((obs, o, skin) -> {
            if (skin != null) {
                Platform.runLater(this::calibrateLineHeight);
            }
        });
    }

    TextArea editor() {
        return code;
    }

    boolean hasContent() {
        return code.getText() != null && !code.getText().isBlank();
    }

    void setContent(String content) {
        String text = content == null ? "" : content;
        code.setText(text);
        findAnchor = 0;
        refreshGutter();
    }

    boolean findNext(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String text = code.getText();
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase();
        String q = query.toLowerCase();
        int idx = lower.indexOf(q, findAnchor);
        if (idx < 0 && findAnchor > 0) {
            findAnchor = 0;
            idx = lower.indexOf(q, 0);
        }
        if (idx < 0) {
            return false;
        }
        code.selectRange(idx, idx + query.length());
        code.requestFocus();
        findAnchor = idx + query.length();
        return true;
    }

    /** Returns 1-based line selection snippet, or null if nothing selected. */
    EditorSelectionSnapshot selectionSnapshot() {
        String selected = code.getSelectedText();
        if (selected == null || selected.isBlank()) {
            return null;
        }
        int start = code.getSelection().getStart();
        int end = code.getSelection().getEnd();
        int startLine = lineNumberAt(start) + 1;
        int endLine = lineNumberAt(Math.max(start, end - 1)) + 1;
        String text = selected.length() > 2000 ? selected.substring(0, 2000) + "..." : selected;
        return new EditorSelectionSnapshot(startLine, endLine, text);
    }

    record EditorSelectionSnapshot(int startLine, int endLine, String text) {}

    private int lineNumberAt(int offset) {
        String text = code.getText();
        if (text == null || text.isEmpty() || offset <= 0) {
            return 0;
        }
        int line = 0;
        int limit = Math.min(offset, text.length());
        for (int i = 0; i < limit; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    void resetFind() {
        findAnchor = 0;
    }

    /** Mirrors {@link TextArea#getScrollTop()} negation for gutter translateY. */
    static double gutterTranslateY(double scrollTop) {
        return -scrollTop;
    }

    private void forwardScrollToCode(ScrollEvent event) {
        double next = Math.max(0, code.getScrollTop() - event.getDeltaY());
        code.setScrollTop(next);
        event.consume();
    }

    private void calibrateLineHeight() {
        lineHeight = Math.max(13, code.getFont().getSize() * 1.25);
        refreshGutter();
    }

    private void refreshGutter() {
        String text = code.getText();
        int lines = (text == null || text.isEmpty()) ? 0
                : text.chars().filter(ch -> ch == '\n').map(ch -> 1).sum() + 1;
        gutter.getChildren().clear();
        if (lines == 0) {
            return;
        }
        int width = String.valueOf(lines).length();
        double lh = lineHeight;
        for (int i = 1; i <= lines; i++) {
            Label num = new Label(String.format("%" + width + "d", i));
            num.setFont(code.getFont());
            num.setAlignment(Pos.CENTER_RIGHT);
            num.setMaxWidth(Double.MAX_VALUE);
            num.getStyleClass().add("gutter-line");
            num.setMinHeight(lh);
            num.setPrefHeight(lh);
            num.setMaxHeight(lh);
            gutter.getChildren().add(num);
        }
    }
}
