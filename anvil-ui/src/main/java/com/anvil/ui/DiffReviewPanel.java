package com.anvil.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 多文件编辑对比面板：左侧文件队列 + 右侧 Before/After 并排 diff。
 *
 * <p>Agent 每次 {@code edit.preview} 追加/更新队列中的一项；可逐文件 Accept/Reject，或批量处理。</p>
 */
final class DiffReviewPanel extends VBox {

    record PendingEdit(String path, String previousContent, String newContent) {}

    record HunkDecisionEvent(String path, int hunkIndex, DiffEngine.HunkDecision decision) {}

    record DiffRow(int lineNo, String marker, String oldLine, String newLine, DiffKind kind) {}

    enum DiffKind { CONTEXT, ADDED, REMOVED }

    private static final class FileDiffState {
        PendingEdit edit;
        List<DiffRow> rows = List.of();
        List<DiffEngine.DiffHunk> hunks = List.of();
        boolean unified;
        boolean loading;
        int added;
        int removed;
    }

    private final Label queueLabel = new Label("0 files");
    private final Label pathLabel = new Label();
    private final Label beforeHeader = new Label("Before");
    private final Label afterHeader = new Label("After");
    private final Label beforeStats = new Label("");
    private final Label afterStats = new Label("");
    private final ListView<String> fileList = new ListView<>();
    private final ScrollPane beforeScroll = new ScrollPane();
    private final ScrollPane afterScroll = new ScrollPane();
    private final VBox beforeLines = new VBox();
    private final VBox afterLines = new VBox();
    private final Button acceptBtn = new Button("Accept");
    private final Button rejectBtn = new Button("Reject");
    private final Button acceptAllBtn = new Button("Accept All");
    private final Button acceptHunkBtn = new Button("Accept Hunk");
    private final Button rejectHunkBtn = new Button("Reject Hunk");
    private final ListView<String> hunkList = new ListView<>();
    private final Button rejectAllBtn = new Button("Reject All");

    /** path → diff state（LinkedHashMap 保持到达顺序）。 */
    private final LinkedHashMap<String, FileDiffState> pendingFiles = new LinkedHashMap<>();
    private String selectedPath;
    private Consumer<PendingEdit> onAccept = edit -> {};
    private Consumer<PendingEdit> onReject = edit -> {};
    private Runnable onQueueEmpty = () -> {};
    private Consumer<HunkDecisionEvent> onHunkDecision = ev -> {};

    /** Approximate monospace char width (px) for horizontal sizing. */
    private static final double CHAR_PX = 7.8;
    private static final double MIN_CODE_WIDTH_PX = 320;
    private static final double MAX_CODE_WIDTH_PX = 2400;

    DiffReviewPanel() {
        super(10);
        getStyleClass().add("diff-review-panel");
        setPadding(new Insets(8));

        pathLabel.getStyleClass().add("diff-review-path");
        beforeHeader.getStyleClass().add("diff-review-header");
        afterHeader.getStyleClass().add("diff-review-header");
        beforeStats.getStyleClass().add("diff-review-stats");
        afterStats.getStyleClass().add("diff-review-stats");
        queueLabel.getStyleClass().add("diff-review-stats");

        fileList.getStyleClass().add("diff-file-list");
        fileList.setPrefWidth(180);
        fileList.setMinWidth(140);
        fileList.setMaxWidth(240);
        fileList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String path, boolean empty) {
                super.updateItem(path, empty);
                if (empty || path == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                FileDiffState state = pendingFiles.get(path);
                String stats = "";
                if (state != null) {
                    if (state.loading) {
                        stats = " …";
                    } else if (state.added > 0 || state.removed > 0) {
                        stats = " +" + state.added + " -" + state.removed;
                    }
                }
                setText(shortName(path) + stats);
            }
        });
        fileList.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, old, path) -> {
                    if (path != null) {
                        selectFile(path);
                    }
                });

        configureCodeScroll(beforeScroll, beforeLines, "diff-code-pane");
        configureCodeScroll(afterScroll, afterLines, "diff-code-pane");
        beforeScroll.vvalueProperty().bindBidirectional(afterScroll.vvalueProperty());
        beforeScroll.hvalueProperty().bindBidirectional(afterScroll.hvalueProperty());

        acceptBtn.getStyleClass().add("primary-btn");
        rejectBtn.getStyleClass().add("danger-btn");
        acceptAllBtn.getStyleClass().add("accent-btn");
        rejectAllBtn.getStyleClass().add("danger-btn");

        acceptBtn.setOnAction(e -> acceptSelected());
        rejectBtn.setOnAction(e -> rejectSelected());
        acceptAllBtn.setOnAction(e -> acceptAll());
        rejectAllBtn.setOnAction(e -> rejectAll());
        acceptHunkBtn.getStyleClass().add("accent-btn");
        rejectHunkBtn.getStyleClass().add("danger-btn");
        acceptHunkBtn.setOnAction(e -> decideSelectedHunk(DiffEngine.HunkDecision.ACCEPTED));
        rejectHunkBtn.setOnAction(e -> decideSelectedHunk(DiffEngine.HunkDecision.REJECTED));

        hunkList.setPrefHeight(72);
        hunkList.getStyleClass().add("diff-hunk-list");
        hunkList.getSelectionModel()
                .selectedIndexProperty()
                .addListener((obs, old, idx) -> scrollToSelectedHunk());

        HBox beforeHeadBox = new HBox(6, beforeHeader, beforeStats);
        beforeHeadBox.setAlignment(Pos.CENTER_LEFT);
        HBox afterHeadBox = new HBox(6, afterHeader, afterStats);
        afterHeadBox.setAlignment(Pos.CENTER_LEFT);

        VBox beforeBox = new VBox(3, beforeHeadBox);
        beforeBox.setAlignment(Pos.TOP_LEFT);
        VBox afterBox = new VBox(3, afterHeadBox);
        afterBox.setAlignment(Pos.TOP_LEFT);

        HBox headers = new HBox(beforeBox, afterBox);
        HBox.setHgrow(beforeBox, Priority.ALWAYS);
        HBox.setHgrow(afterBox, Priority.ALWAYS);
        headers.setMinWidth(400);

        SplitPane codeSplit = new SplitPane(beforeScroll, afterScroll);
        codeSplit.setDividerPositions(0.5);
        VBox.setVgrow(codeSplit, Priority.ALWAYS);

        HBox fileActions = new HBox(8, acceptBtn, rejectBtn, acceptHunkBtn, rejectHunkBtn);
        fileActions.setAlignment(Pos.CENTER_LEFT);

        HBox batchActions = new HBox(8, queueLabel, new javafx.scene.layout.Region(), acceptAllBtn, rejectAllBtn);
        HBox.setHgrow(batchActions.getChildren().get(1), Priority.ALWAYS);
        batchActions.setAlignment(Pos.CENTER_LEFT);

        VBox diffPane = new VBox(6, pathLabel, hunkList, headers, codeSplit, fileActions);
        VBox.setVgrow(codeSplit, Priority.ALWAYS);

        SplitPane split = new SplitPane(fileList, diffPane);
        split.setDividerPositions(0.18);
        VBox.setVgrow(split, Priority.ALWAYS);

        getChildren().addAll(batchActions, split);
        updateQueueLabel();
    }

    int pendingCount() {
        return pendingFiles.size();
    }

    boolean isEmpty() {
        return pendingFiles.isEmpty();
    }

    void showLoading(PendingEdit edit) {
        FileDiffState state = pendingFiles.computeIfAbsent(edit.path(), k -> new FileDiffState());
        state.edit = edit;
        state.loading = true;
        state.rows = List.of();
        state.added = 0;
        state.removed = 0;
        refreshFileList();
        selectFile(edit.path());
        clearCodePanes();
        beforeLines.getChildren().add(placeholderLabel("Computing diff…"));
        afterLines.getChildren().add(placeholderLabel(""));
        beforeStats.setText("");
        afterStats.setText("");
    }

    void show(PendingEdit edit, List<DiffRow> rows) {
        FileDiffState state = pendingFiles.computeIfAbsent(edit.path(), k -> new FileDiffState());
        state.edit = edit;
        state.loading = false;
        state.unified = false;
        state.rows = rows;

        int added = 0;
        int removed = 0;
        for (DiffRow r : rows) {
            if (r.kind() == DiffKind.ADDED) {
                added++;
            } else if (r.kind() == DiffKind.REMOVED) {
                removed++;
            }
        }
        state.added = added;
        state.removed = removed;
        state.hunks = DiffEngine.hunks(rows);

        refreshFileList();
        refreshHunkList(state);
        if (selectedPath == null || selectedPath.equals(edit.path())) {
            selectFile(edit.path());
        }
    }

    void showDiffText(String path, String unifiedDiff) {
        FileDiffState state = pendingFiles.computeIfAbsent(path, k -> new FileDiffState());
        state.edit = new PendingEdit(path, "", "");
        state.loading = false;
        state.unified = true;
        state.added = 0;
        state.removed = 0;

        List<DiffRow> rows = new ArrayList<>();
        int lineNo = 1;
        for (String line : unifiedDiff.split("\n", -1)) {
            char marker = line.isEmpty() ? ' ' : line.charAt(0);
            DiffKind kind =
                    marker == '+' ? DiffKind.ADDED : marker == '-' ? DiffKind.REMOVED : DiffKind.CONTEXT;
            String body = line.startsWith("+ ") || line.startsWith("- ") ? line.substring(2) : line;
            rows.add(new DiffRow(lineNo++, line.isEmpty() ? " " : String.valueOf(marker), body, body, kind));
        }
        state.rows = rows;
        refreshFileList();
        selectFile(path);
    }

    void setOnAccept(Consumer<PendingEdit> handler) {
        onAccept = handler == null ? edit -> {} : handler;
    }

    void setOnReject(Consumer<PendingEdit> handler) {
        onReject = handler == null ? edit -> {} : handler;
    }

    void setOnQueueEmpty(Runnable handler) {
        onQueueEmpty = handler == null ? () -> {} : handler;
    }

    void setOnHunkDecision(Consumer<HunkDecisionEvent> handler) {
        onHunkDecision = handler == null ? ev -> {} : handler;
    }

    List<DiffEngine.DiffHunk> hunksFor(String path) {
        FileDiffState state = pendingFiles.get(path);
        return state == null ? List.of() : state.hunks;
    }

    PendingEdit pendingEditFor(String path) {
        FileDiffState state = pendingFiles.get(path);
        return state == null ? null : state.edit;
    }

    List<DiffRow> rowsFor(String path) {
        FileDiffState state = pendingFiles.get(path);
        return state == null || state.rows == null ? List.of() : state.rows;
    }

    void updateHunks(String path, List<DiffEngine.DiffHunk> hunks) {
        FileDiffState state = pendingFiles.get(path);
        if (state != null) {
            state.hunks = hunks;
            refreshHunkList(state);
        }
    }

    private void acceptSelected() {
        PendingEdit edit = currentEdit();
        if (edit == null) {
            return;
        }
        removeFile(edit.path());
        onAccept.accept(edit);
        afterFileRemoved();
    }

    private void rejectSelected() {
        PendingEdit edit = currentEdit();
        if (edit == null) {
            return;
        }
        removeFile(edit.path());
        onReject.accept(edit);
        afterFileRemoved();
    }

    private void acceptAll() {
        List<PendingEdit> all = pendingFiles.values().stream()
                .map(s -> s.edit)
                .filter(e -> e != null)
                .toList();
        pendingFiles.clear();
        selectedPath = null;
        clearDiffView();
        refreshFileList();
        for (PendingEdit edit : all) {
            onAccept.accept(edit);
        }
        onQueueEmpty.run();
    }

    private void rejectAll() {
        List<PendingEdit> all = pendingFiles.values().stream()
                .map(s -> s.edit)
                .filter(e -> e != null)
                .toList();
        pendingFiles.clear();
        selectedPath = null;
        clearDiffView();
        refreshFileList();
        for (PendingEdit edit : all) {
            onReject.accept(edit);
        }
        onQueueEmpty.run();
    }

    private void afterFileRemoved() {
        if (pendingFiles.isEmpty()) {
            selectedPath = null;
            clearDiffView();
            onQueueEmpty.run();
            return;
        }
        String next = pendingFiles.keySet().iterator().next();
        fileList.getSelectionModel().select(next);
        selectFile(next);
    }

    private PendingEdit currentEdit() {
        if (selectedPath == null) {
            return null;
        }
        FileDiffState state = pendingFiles.get(selectedPath);
        return state == null ? null : state.edit;
    }

    private void selectFile(String path) {
        selectedPath = path;
        FileDiffState state = pendingFiles.get(path);
        if (state == null) {
            clearDiffView();
            return;
        }
        pathLabel.setText("Edit preview · " + path
                + (state.unified ? " (unified diff — file too large for side-by-side)" : ""));

        if (state.loading) {
            clearCodePanes();
            beforeLines.getChildren().add(placeholderLabel("Computing diff…"));
            afterLines.getChildren().add(placeholderLabel(""));
            beforeStats.setText("");
            afterStats.setText("");
            return;
        }

        List<DiffRow> rows = state.rows == null ? List.of() : state.rows;
        renderDiffRows(rows);
        refreshHunkList(state);
        beforeStats.setText(state.removed > 0 ? "−" + state.removed : "");
        afterStats.setText(state.added > 0 ? "+" + state.added : (state.unified ? "(large file)" : ""));

        if (!rows.isEmpty()) {
            int firstChange = 0;
            for (int k = 0; k < rows.size(); k++) {
                if (rows.get(k).kind() != DiffKind.CONTEXT) {
                    firstChange = k;
                    break;
                }
            }
            double fraction = firstChange / (double) Math.max(1, rows.size());
            beforeScroll.setVvalue(fraction);
        }
    }

    private void renderDiffRows(List<DiffRow> rows) {
        clearCodePanes();
        if (rows.isEmpty()) {
            beforeLines.getChildren().add(placeholderLabel("No changes"));
            afterLines.getChildren().add(placeholderLabel(""));
            return;
        }

        int maxChars = 1;
        for (DiffRow row : rows) {
            maxChars = Math.max(maxChars, row.oldLine().length());
            maxChars = Math.max(maxChars, row.newLine().length());
        }
        double codeWidth = Math.min(MAX_CODE_WIDTH_PX, Math.max(MIN_CODE_WIDTH_PX, maxChars * CHAR_PX + 24));
        beforeLines.setMinWidth(codeWidth);
        afterLines.setMinWidth(codeWidth);

        for (DiffRow row : rows) {
            beforeLines.getChildren().add(codeLine(row.lineNo(), row.marker(), row.oldLine(), row.kind(), true));
            afterLines.getChildren().add(codeLine(row.lineNo(), row.marker(), row.newLine(), row.kind(), false));
        }
    }

    private void refreshHunkList(FileDiffState state) {
        if (state == null || state.hunks.isEmpty() || state.unified) {
            hunkList.setItems(FXCollections.observableArrayList());
            hunkList.setVisible(false);
            acceptHunkBtn.setDisable(true);
            rejectHunkBtn.setDisable(true);
            return;
        }
        hunkList.setVisible(true);
        List<String> labels = new ArrayList<>();
        for (DiffEngine.DiffHunk h : state.hunks) {
            String status = switch (h.decision()) {
                case ACCEPTED -> " ✓";
                case REJECTED -> " ✗";
                case PENDING -> "";
            };
            labels.add("Hunk " + (h.index() + 1) + " +" + h.added() + " -" + h.removed() + status);
        }
        hunkList.setItems(FXCollections.observableArrayList(labels));
        if (!labels.isEmpty()) {
            hunkList.getSelectionModel().select(0);
        }
        acceptHunkBtn.setDisable(false);
        rejectHunkBtn.setDisable(false);
    }

    private void decideSelectedHunk(DiffEngine.HunkDecision decision) {
        if (selectedPath == null) {
            return;
        }
        FileDiffState state = pendingFiles.get(selectedPath);
        if (state == null || state.hunks.isEmpty()) {
            return;
        }
        int idx = hunkList.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= state.hunks.size()) {
            return;
        }
        List<DiffEngine.DiffHunk> updated = new ArrayList<>(state.hunks);
        updated.set(idx, DiffEngine.withDecision(state.hunks.get(idx), decision));
        state.hunks = updated;
        refreshHunkList(state);
        onHunkDecision.accept(new HunkDecisionEvent(selectedPath, idx, decision));
        if (DiffEngine.allHunksResolved(state.hunks)) {
            appendHunkSummary(state);
        }
    }

    private void appendHunkSummary(FileDiffState state) {
        long rejected = state.hunks.stream().filter(h -> h.decision() == DiffEngine.HunkDecision.REJECTED).count();
        if (rejected == state.hunks.size() && state.edit != null) {
            rejectSelected();
        }
    }

    private void scrollToSelectedHunk() {
        if (selectedPath == null) {
            return;
        }
        FileDiffState state = pendingFiles.get(selectedPath);
        if (state == null || state.rows.isEmpty()) {
            return;
        }
        int idx = hunkList.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= state.hunks.size()) {
            return;
        }
        DiffEngine.DiffHunk hunk = state.hunks.get(idx);
        double fraction = hunk.rowStart() / (double) Math.max(1, state.rows.size());
        beforeScroll.setVvalue(fraction);
    }

    private void clearCodePanes() {
        beforeLines.getChildren().clear();
        afterLines.getChildren().clear();
    }

    private static void configureCodeScroll(ScrollPane scroll, VBox lines, String styleClass) {
        scroll.setContent(lines);
        scroll.setFitToWidth(false);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().addAll("diff-code-scroll", styleClass);
        lines.getStyleClass().add("diff-code-lines");
        VBox.setVgrow(scroll, Priority.ALWAYS);
    }

    private static Label placeholderLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("diff-empty-hint");
        return label;
    }

    private static HBox codeLine(int lineNo, String marker, String text, DiffKind kind, boolean beforeSide) {
        Label num = new Label(String.valueOf(lineNo));
        num.getStyleClass().add("diff-gutter-num");
        num.setMinWidth(36);

        Label mark = new Label(marker == null ? " " : marker);
        mark.getStyleClass().add("diff-gutter-mark");
        mark.setMinWidth(14);

        Text code = new Text(text == null ? "" : text);
        code.getStyleClass().add("diff-line-text");

        HBox row = new HBox(4, num, mark, code);
        row.getStyleClass().add("diff-code-row");
        row.setMinHeight(22);
        row.setAlignment(Pos.CENTER_LEFT);

        if (kind == DiffKind.ADDED && !beforeSide) {
            row.getStyleClass().add("diff-row-added");
        } else if (kind == DiffKind.REMOVED && beforeSide) {
            row.getStyleClass().add("diff-row-removed");
        }
        return row;
    }

    private void removeFile(String path) {
        pendingFiles.remove(path);
        refreshFileList();
    }

    private void refreshFileList() {
        ObservableList<String> items = FXCollections.observableArrayList(pendingFiles.keySet());
        fileList.setItems(items);
        updateQueueLabel();
        if (selectedPath != null && items.contains(selectedPath)) {
            fileList.getSelectionModel().select(selectedPath);
        } else if (!items.isEmpty()) {
            fileList.getSelectionModel().select(0);
        }
    }

    private void updateQueueLabel() {
        int n = pendingFiles.size();
        queueLabel.setText(n == 0 ? "No pending edits" : n + " file" + (n == 1 ? "" : "s") + " pending");
        acceptAllBtn.setDisable(n <= 1);
        rejectAllBtn.setDisable(n <= 1);
        acceptBtn.setDisable(n == 0);
        rejectBtn.setDisable(n == 0);
    }

    private void clearDiffView() {
        clearCodePanes();
        beforeLines.getChildren().add(placeholderLabel("No changes"));
        afterLines.getChildren().add(placeholderLabel(""));
        pathLabel.setText("");
        beforeStats.setText("");
        afterStats.setText("");
    }

    private static String shortName(String path) {
        int idx = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return idx >= 0 ? path.substring(idx + 1) : path;
    }
}
