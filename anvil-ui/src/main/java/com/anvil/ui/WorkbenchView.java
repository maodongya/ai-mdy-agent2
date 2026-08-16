package com.anvil.ui;

import com.anvil.protocol.ApprovalDecision;
import com.anvil.protocol.Event;
import com.anvil.ui.client.AnvilClient;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class WorkbenchView {

    private final UiSettings settings;
    private final int windowId;
    private final Consumer<String> titleUpdater;
    private final BorderPane root = new BorderPane();

    private final TextField serverField = new TextField();
    private final TextField workspaceField = new TextField();
    private final Label healthLabel = new Label("…");
    private final ComboBox<String> modeBox = new ComboBox<>();
    private final ComboBox<String> profileBox = new ComboBox<>();
    private final ComboBox<String> modelBox = new ComboBox<>();
    private final TreeView<FileTreeBuilder.FileNode> fileTree = new TreeView<>();
    private final TextField fileFilter = new TextField();
    private final Label editorTitle = new Label("Editor");
    private final TabPane editorTabs = new TabPane();
    private final Label editorEmptyHint = new Label("Select a file from Explorer");
    private final EditorFindBar findBar = new EditorFindBar();
    private final ProblemsPanel problemsPanel = new ProblemsPanel();
    private final ConsolePanel consolePanel = new ConsolePanel();
    private final RunDetailPanel runDetailPanel = new RunDetailPanel();
    private final DiffReviewPanel diffReviewPanel = new DiffReviewPanel();
    /** 中间区域标签页（Sheet1=代码 / Sheet2=对比）。 */
    private TabPane centerTabs;
    /** 对比标签（Sheet2），用于收到 edit.preview 时自动切换。 */
    private Tab diffTab;
    private Tab codeTab;
    private final TextField promptField = new TextField();
    private final Button runBtn = new Button("Run ▶");
    private final Button cancelBtn = new Button("Stop");
    private final Button connectBtn = new Button("Connect");
    private final Button browseBtn = new Button("…");
    private final Button refreshFilesBtn = new Button("↻");
    private final Button copyConsoleBtn = new Button("Copy");
    private final Button exportTraceBtn = new Button("CSV");
    private final Button newWindowBtn = new Button("+ Window");
    private final Label statusLabel = new Label("Ready");
    private final Label threadLabel = new Label("");
    private final Label runLabel = new Label("");
    private final Label metricsLabel = new Label("");
    private final Label runStatusLabel = new Label("");
    private final CheckBox autoApproveWritesCheck = new CheckBox("Yolo writes");
    private final ProgressIndicator runIndicator = new ProgressIndicator();

    private SplitPane mainSplit;
    private SplitPane centerSplit;
    private SplitPane verticalSplit;

    private AnvilClient client;
    private TerminalClient terminalClient;
    /** 底部终端面板（M2/M3）。 */
    private SessionTerminalPanel terminalPanel;
    private boolean terminalCollapsed;
    private double terminalDividerBeforeCollapse = 0.72;
    /** tool_call_id → shell.exec command（用于终端镜像）。 */
    private final Map<String, String> pendingShellCommands = new HashMap<>();
    private String threadId;
    private String currentRunId;
    private volatile boolean running;
    private volatile boolean closed;
    private volatile boolean pendingTreeRefresh;
    private volatile Thread sseThread;

    private List<String> allFilePaths;
    private Set<String> allFilePathSet = Set.of();
    private final Set<String> expandedDirPaths = new HashSet<>();
    private String fileFilterText = "";
    private final List<String> promptHistory = new ArrayList<>();
    private int promptHistoryIndex = -1;

    /** 已打开的文件标签页：path → 对应的编辑器 */
    private final Map<String, CodeEditorPane> openEditors = new LinkedHashMap<>();
    /** 已打开的文件标签页：path → Tab 节点 */
    private final Map<String, Tab> openTabs = new HashMap<>();
    /** Agent 修改待 Accept 的文件路径 */
    private final Set<String> modifiedFilePaths = new HashSet<>();
    /** 用户编辑未保存的文件路径 (Phase 9.1) */
    private final Set<String> dirtyEditorPaths = new HashSet<>();

    private final ExecutorService bg;
    private final PauseTransition filterDebounce = new PauseTransition(Duration.millis(300));
    private final UiRunEventPump eventPump =
            new UiRunEventPump(new UiRunEventPump.Listener() {
                @Override
                public void onDeltaBatch(String text) {
                    consolePanel.appendDelta(text);
                }

                @Override
                public void onEvent(Event event) {
                    handleEvent(event);
                }

                @Override
                public void onFrameEnd() {
                    runDetailPanel.flushPendingRows();
                }
            });
    private final PauseTransition finishRunDebounce = new PauseTransition(Duration.millis(150));

    public WorkbenchView(UiSettings settings, int windowId, Consumer<String> titleUpdater) {
        this.settings = settings;
        this.windowId = windowId;
        this.titleUpdater = titleUpdater;
        this.promptHistory.addAll(settings.recentPrompts());
        this.bg = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "anvil-ui-" + windowId);
            t.setDaemon(true);
            return t;
        });
        finishRunDebounce.setOnFinished(e -> finishRunDeferred());
        buildUi();
    }

    public BorderPane getRoot() {
        return root;
    }

    public void setMenuBar(MenuBar menuBar) {
        VBox top = new VBox(menuBar, root.getTop());
        root.setTop(top);
    }

    public void registerShortcuts(Scene scene) {
        scene.getAccelerators().put(KeyCombination.keyCombination("Shortcut+F"), this::showFindBar);
        scene.getAccelerators().put(KeyCombination.keyCombination("Shortcut+S"), this::saveCurrentFile);
        scene.getAccelerators().put(KeyCombination.keyCombination("F12"), this::goToDefinition);
        scene.getAccelerators().put(KeyCombination.keyCombination("Shift+F12"), this::findReferences);
        scene.getAccelerators().put(KeyCombination.keyCombination("Escape"), findBar::hide);
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+BACK_QUOTE"), this::toggleTerminal);
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+Shift+5"), this::focusTerminalInput);
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+Tab"), this::nextTerminalTab);
    }

    public void toggleTerminal() {
        if (verticalSplit == null || terminalPanel == null) {
            return;
        }
        if (terminalCollapsed) {
            terminalCollapsed = false;
            settings.setTerminalVisible(true);
            terminalPanel.setVisible(true);
            terminalPanel.setManaged(true);
            verticalSplit.setDividerPositions(terminalDividerBeforeCollapse);
        } else {
            terminalDividerBeforeCollapse = verticalSplit.getDividerPositions()[0];
            settings.saveTerminalDivider(terminalDividerBeforeCollapse);
            terminalCollapsed = true;
            settings.setTerminalVisible(false);
            terminalPanel.setVisible(false);
            terminalPanel.setManaged(false);
            verticalSplit.setDividerPositions(1.0);
        }
        submitBg(settings::save);
    }

    public void focusTerminalInput() {
        if (terminalPanel == null) {
            return;
        }
        if (terminalCollapsed) {
            toggleTerminal();
        }
        terminalPanel.focusInput();
    }

    public void nextTerminalTab() {
        if (terminalPanel != null) {
            terminalPanel.nextTab();
        }
    }

    public void connectOnStartup() {
        serverField.setText(settings.serverUrl());
        workspaceField.setText(settings.workspacePath());
        modelBox.setValue(settings.defaultModel());
        runIndicator.setVisible(false);
        runIndicator.setPrefSize(16, 16);
        runIndicator.setMaxSize(16, 16);
        cancelBtn.setVisible(false);
        cancelBtn.setManaged(false);
        refreshHealth();
        connect();
    }

    public void refreshTree() {
        refreshTreeAsync();
    }

    public void clearConsole() {
        consolePanel.clear();
        runDetailPanel.clear();
    }

    public void submitRun() {
        submitRunInternal();
    }

    public void cancelRun() {
        cancelRunInternal();
    }

    public void showPreferences() {
        if (PreferencesDialog.show(settings, root.getScene().getWindow())) {
            serverField.setText(settings.serverUrl());
            workspaceField.setText(settings.workspacePath());
            modelBox.setValue(settings.defaultModel());
        }
    }

    public void copyConsole() {
        if (consolePanel.isEmpty()) {
            return;
        }
        Clipboard.getSystemClipboard().setContent(new javafx.scene.input.ClipboardContent() {
            {
                putString(consolePanel.copyAllText());
            }
        });
        setStatus("Console copied");
    }

    public void exportTraceCsv() {
        if (runDetailPanel.isEmpty()) {
            setStatus("Trace is empty");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export run trace");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        chooser.setInitialFileName("anvil-trace-" + (currentRunId != null ? currentRunId : "run") + ".csv");
        File target = chooser.showSaveDialog(root.getScene().getWindow());
        if (target == null) {
            return;
        }
        submitBg(() -> {
            try {
                java.nio.file.Files.writeString(target.toPath(), runDetailPanel.exportCsv());
                ui(() -> setStatus("Trace exported · " + target.getName()));
            } catch (Exception e) {
                ui(() -> appendConsole(ConsoleLine.Kind.ERROR, e.getMessage()));
            }
        });
    }

    public void shutdown() {
        closed = true;
        finishRunDebounce.stop();
        eventPump.stop();
        interruptSse();
        if (mainSplit != null && centerSplit != null) {
            settings.saveDividers(mainSplit.getDividerPositions()[0], centerSplit.getDividerPositions()[0]);
        }
        if (verticalSplit != null && !terminalCollapsed) {
            settings.saveTerminalDivider(verticalSplit.getDividerPositions()[0]);
        }
        bg.shutdownNow();
    }

    private void submitBg(Runnable task) {
        if (closed || bg.isShutdown()) {
            return;
        }
        try {
            bg.submit(task);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // Window closed while a deferred UI callback still tried to enqueue work.
        }
    }

    private void interruptSse() {
        Thread t = sseThread;
        if (t != null) {
            t.interrupt();
        }
        sseThread = null;
    }

    private void buildUi() {
        root.getStyleClass().add("root");
        Font mono = Font.font("Monospaced", 12);

        Label logo = new Label("ANVIL");
        logo.getStyleClass().add("logo");
        logo.setFont(Font.font("Monospaced", FontWeight.BOLD, 18));

        serverField.setPromptText("http://127.0.0.1:7788");
        serverField.setPrefWidth(140);
        serverField.setFont(mono);
        Tooltip.install(serverField, new Tooltip("App Server URL"));

        workspaceField.setPromptText("Workspace directory");
        workspaceField.setPrefWidth(260);
        workspaceField.setFont(mono);
        HBox.setHgrow(workspaceField, Priority.SOMETIMES);

        browseBtn.getStyleClass().add("icon-btn");
        browseBtn.setTooltip(new Tooltip("Browse folder"));
        browseBtn.setOnAction(e -> browseWorkspace());

        modeBox.getItems().addAll("ask", "agent", "plan", "debug");
        modeBox.setValue("agent");
        modeBox.setPrefWidth(86);

        profileBox.getItems().addAll("extended", "standard", "complex");
        profileBox.setValue("extended");
        profileBox.setPrefWidth(96);
        profileBox.setTooltip(new Tooltip("Run depth: standard=40 steps, extended=100, complex=200 + plan mode"));

        modelBox.getItems().addAll(ModelPresets.all());
        modelBox.setEditable(true);
        modelBox.setPrefWidth(210);
        modelBox.getStyleClass().add("model-combo");

        connectBtn.setOnAction(e -> connect());
        newWindowBtn.setOnAction(e -> WorkbenchWindow.openNew());
        newWindowBtn.getStyleClass().add("accent-btn");

        runBtn.getStyleClass().add("primary");
        cancelBtn.getStyleClass().add("danger-btn");
        cancelBtn.setOnAction(e -> cancelRunInternal());
        runIndicator.setVisible(false);
        runIndicator.setManaged(false);
        runStatusLabel.getStyleClass().add("run-status-label");
        runStatusLabel.setVisible(false);
        runStatusLabel.setManaged(false);
        healthLabel.getStyleClass().add("health-badge");

        HBox toolbar = new HBox(
                8,
                logo,
                labeledField("Server", serverField),
                labeledField("Workspace", new HBox(4, workspaceField, browseBtn)),
                connectBtn,
                healthLabel,
                new Separator(Orientation.VERTICAL),
                labeledField("Mode", modeBox),
                labeledField("Profile", profileBox),
                labeledField("Model", modelBox),
                new Separator(Orientation.VERTICAL),
                newWindowBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10, 14, 10, 14));
        toolbar.getStyleClass().add("toolbar");
        root.setTop(toolbar);

        fileTree.setShowRoot(true);
        fileTree.setPrefWidth(260);
        fileTree.setMinWidth(180);
        fileTree.getStyleClass().add("file-tree");
        fileTree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(FileTreeBuilder.FileNode item, boolean empty) {
                super.updateItem(item, empty);
                setOnMouseClicked(null);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText((item.directory() ? "📁 " : "📄 ") + item.label());
                setGraphic(null);
                getStyleClass().removeAll("tree-dir", "tree-file");
                getStyleClass().add(item.directory() ? "tree-dir" : "tree-file");
                if (item.directory()) {
                    setOnMouseClicked(e -> {
                        if (e.getClickCount() != 1) {
                            return;
                        }
                        TreeItem<FileTreeBuilder.FileNode> node = getTreeItem();
                        if (node == null) {
                            return;
                        }
                        node.setExpanded(true);
                        if (item.fullPath() != null) {
                            expandedDirPaths.add(item.fullPath());
                        }
                    });
                }
            }
        });
        fileTree.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, old, item) -> {
                    if (item != null
                            && item.getValue() != null
                            && !item.getValue().directory()
                            && item.getValue().fullPath() != null) {
                        openFile(item.getValue().fullPath());
                    }
                });

        fileFilter.setPromptText("Filter files…");
        filterDebounce.setOnFinished(e -> applyFileFilter(fileFilter.getText()));
        fileFilter.textProperty().addListener((obs, old, filter) -> filterDebounce.playFromStart());

        refreshFilesBtn.getStyleClass().add("icon-btn");
        refreshFilesBtn.setOnAction(e -> refreshTreeAsync());

        copyConsoleBtn.getStyleClass().add("icon-btn");
        copyConsoleBtn.setOnAction(e -> copyConsole());

        exportTraceBtn.getStyleClass().add("icon-btn");
        exportTraceBtn.setTooltip(new Tooltip("Export Trace tab to CSV"));
        exportTraceBtn.setOnAction(e -> exportTraceCsv());

        HBox filesHeader = new HBox(6, sectionLabel("Explorer"), new Region(), refreshFilesBtn);
        HBox.setHgrow(filesHeader.getChildren().get(1), Priority.ALWAYS);
        filesHeader.setAlignment(Pos.CENTER_LEFT);

        VBox sidebar = new VBox(8, filesHeader, fileFilter, fileTree);
        VBox.setVgrow(fileTree, Priority.ALWAYS);
        sidebar.getStyleClass().add("panel");
        sidebar.setPadding(new Insets(10));

        editorTitle.getStyleClass().add("editor-path");
        editorTabs.getStyleClass().add("editor-tabs");
        editorTabs.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        editorTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        editorTabs.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) change -> {
            while (change.next()) {
                if (change.wasRemoved()) {
                    for (Tab tab : change.getRemoved()) {
                        unregisterEditorTab(tab);
                    }
                }
            }
        });
        editorTabs.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, old, t) -> {
                    updateEditorEmptyHint();
                    clearFindStatus();
                });

        editorEmptyHint.getStyleClass().add("empty-hint");
        StackPane editorStack = new StackPane(editorTabs, editorEmptyHint);
        editorStack.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        updateEditorEmptyHint();

        findBar.bind(this::findInEditor);
        diffReviewPanel.setOnAccept(this::acceptEdit);
        diffReviewPanel.setOnReject(this::revertEdit);
        diffReviewPanel.setOnQueueEmpty(this::onDiffQueueEmpty);
        diffReviewPanel.setOnHunkDecision(this::applyHunkDecision);

        VBox codePane = new VBox(8, editorTitle, findBar, editorStack, problemsPanel);
        VBox.setVgrow(editorStack, Priority.ALWAYS);
        codePane.getStyleClass().add("panel");
        codePane.setPadding(new Insets(10));
        problemsPanel.setOnOpen(row -> openFile(row.path()));

        VBox diffPane = new VBox(8, diffReviewPanel);
        VBox.setVgrow(diffReviewPanel, Priority.ALWAYS);
        diffPane.getStyleClass().add("panel");
        diffPane.setPadding(new Insets(10));

        // Sheet1=代码窗口，Sheet2=对比修改代码的窗口
        codeTab = new Tab("Code", codePane);
        codeTab.setClosable(false);
        codeTab.getStyleClass().add("center-tab");
        diffTab = new Tab("Review Diff", diffPane);
        diffTab.setClosable(false);
        diffTab.getStyleClass().add("center-tab");
        centerTabs = new TabPane(codeTab, diffTab);
        centerTabs.getStyleClass().add("editor-tabs");
        centerTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(centerTabs, Priority.ALWAYS);

        VBox editorPane = new VBox(centerTabs);
        editorPane.setMinWidth(200);

        promptField.setPromptText("Ask Anvil… (@path to reference files, ⌘↩ run)");
        promptField.setFont(mono);
        promptField.setOnAction(e -> submitRunInternal());
        promptField.addEventHandler(KeyEvent.KEY_PRESSED, this::handlePromptHistoryKey);

        runBtn.setOnAction(e -> submitRunInternal());
        Label promptGlyph = new Label("❯");
        promptGlyph.getStyleClass().add("prompt-glyph");
        autoApproveWritesCheck.setSelected(settings.autoApproveWrites());
        autoApproveWritesCheck.setTooltip(new Tooltip("Auto-approve fs.write in Agent mode (like Cursor Yolo)"));
        autoApproveWritesCheck.selectedProperty().addListener((obs, old, selected) -> {
            settings.setAutoApproveWrites(selected);
            submitBg(settings::save);
        });

        HBox inputRow = new HBox(8, promptGlyph, promptField, autoApproveWritesCheck, runStatusLabel, cancelBtn, runBtn);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        inputRow.getStyleClass().add("prompt-row");
        HBox.setHgrow(promptField, Priority.ALWAYS);

        HBox consoleHeader = new HBox(6, sectionLabel("Agent Console"), new Region(), exportTraceBtn, copyConsoleBtn);
        HBox.setHgrow(consoleHeader.getChildren().get(1), Priority.ALWAYS);

        Tab consoleTab = new Tab("Console", consolePanel);
        consoleTab.setClosable(false);
        Tab traceTab = new Tab("Trace", runDetailPanel);
        traceTab.setClosable(false);
        TabPane agentTabs = new TabPane(consoleTab, traceTab);
        agentTabs.getStyleClass().add("agent-tabs");
        VBox.setVgrow(agentTabs, Priority.ALWAYS);

        VBox consolePane = new VBox(8, consoleHeader, agentTabs, inputRow);
        consolePane.getStyleClass().add("panel");
        consolePane.setPadding(new Insets(10));
        consolePane.setPrefWidth(420);
        consolePane.setMinWidth(280);

        centerSplit = new SplitPane(editorPane, consolePane);
        centerSplit.setOrientation(Orientation.HORIZONTAL);
        centerSplit.setDividerPositions(settings.centerDivider());

        mainSplit = new SplitPane(sidebar, centerSplit);
        mainSplit.setDividerPositions(settings.mainDivider());

        String serverUrl = serverField.getText().trim();
        terminalClient = new TerminalClient(serverUrl.isEmpty() ? settings.serverUrl() : serverUrl);
        terminalPanel = new SessionTerminalPanel(mainSplit, terminalClient);
        terminalPanel.getStyleClass().add("panel");
        terminalCollapsed = !settings.terminalVisible();
        terminalDividerBeforeCollapse = settings.terminalDivider();

        verticalSplit = new SplitPane(mainSplit, terminalPanel);
        verticalSplit.setOrientation(Orientation.VERTICAL);
        verticalSplit.setDividerPositions(terminalCollapsed ? 1.0 : terminalDividerBeforeCollapse);
        if (terminalCollapsed) {
            terminalPanel.setVisible(false);
            terminalPanel.setManaged(false);
        }
        root.setCenter(verticalSplit);

        threadLabel.getStyleClass().add("status-meta");
        runLabel.getStyleClass().add("status-meta");
        metricsLabel.getStyleClass().add("status-meta");
        HBox statusBar = new HBox(12, statusLabel, new Separator(Orientation.VERTICAL), threadLabel, runLabel, metricsLabel);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(5, 14, 5, 14));
        statusBar.getStyleClass().add("status-bar");
        root.setBottom(statusBar);
    }

    private void handlePromptHistoryKey(KeyEvent e) {
        if (promptHistory.isEmpty()) {
            return;
        }
        if (e.getCode() == KeyCode.UP) {
            if (promptHistoryIndex < 0) {
                promptHistoryIndex = 0;
            } else if (promptHistoryIndex < promptHistory.size() - 1) {
                promptHistoryIndex++;
            }
            promptField.setText(promptHistory.get(promptHistoryIndex));
            promptField.positionCaret(promptField.getText().length());
            e.consume();
        } else if (e.getCode() == KeyCode.DOWN) {
            if (promptHistoryIndex > 0) {
                promptHistoryIndex--;
                promptField.setText(promptHistory.get(promptHistoryIndex));
            } else {
                promptHistoryIndex = -1;
                promptField.clear();
            }
            e.consume();
        }
    }

    public void saveCurrentFile() {
        Tab selected = editorTabs.getSelectionModel().getSelectedItem();
        if (selected == null || !(selected.getUserData() instanceof String path)) {
            return;
        }
        CodeEditorPane editor = openEditors.get(path);
        if (editor == null || !editor.isDirty()) {
            return;
        }
        saveFile(path, editor.content(), true);
    }

    public void goToDefinition() {
        navigateSymbol(true);
    }

    public void findReferences() {
        navigateSymbol(false);
    }

    private void navigateSymbol(boolean definition) {
        if (client == null || threadId == null) {
            return;
        }
        Tab selected = editorTabs.getSelectionModel().getSelectedItem();
        if (selected == null || !(selected.getUserData() instanceof String path)) {
            return;
        }
        CodeEditorPane editor = openEditors.get(path);
        if (editor == null || !path.endsWith(".java")) {
            setStatus("Go to definition requires a Java file");
            return;
        }
        int line = editor.cursorLine();
        int column = editor.cursorColumn();
        submitBg(() -> {
            try {
                if (definition) {
                    Map<String, Object> loc = client.lspDefinition(threadId, path, line, column);
                    String target = String.valueOf(loc.getOrDefault("path", ""));
                    int targetLine = intPayload(loc.get("line"), 1);
                    ui(() -> {
                        if (!target.isBlank()) {
                            openFile(target);
                            appendConsole(ConsoleLine.Kind.SYSTEM, "definition → " + target + ":" + targetLine);
                        } else {
                            setStatus("No definition found");
                        }
                    });
                } else {
                    List<Map<String, Object>> refs = client.lspReferences(threadId, path, line, column);
                    ui(() -> {
                        appendConsole(ConsoleLine.Kind.SYSTEM, refs.size() + " references");
                        for (Map<String, Object> ref : refs.stream().limit(8).toList()) {
                            appendConsole(
                                    ConsoleLine.Kind.SYSTEM,
                                    "  · "
                                            + ref.get("path")
                                            + ":"
                                            + ref.get("line"));
                        }
                        if (!refs.isEmpty()) {
                            Map<String, Object> first = refs.getFirst();
                            openFile(String.valueOf(first.get("path")));
                        }
                    });
                }
            } catch (Exception e) {
                ui(() -> appendConsole(ConsoleLine.Kind.ERROR, e.getMessage()));
            }
        });
    }

    public void showFindBar() {
        findBar.show();
        CodeEditorPane current = currentEditor();
        if (current != null) {
            current.resetFind();
        }
    }

    private void findInEditor() {
        String q = findBar.queryText();
        CodeEditorPane current = currentEditor();
        if (current == null) {
            findBar.setStatus("");
            return;
        }
        if (current.findNext(q)) {
            findBar.setStatus("Found");
        } else {
            findBar.setStatus(q.isBlank() ? "" : "Not found");
        }
    }

    private void clearFindStatus() {
        findBar.setStatus("");
    }

    private void updateEditorEmptyHint() {
        boolean empty = currentEditor() == null;
        editorEmptyHint.setVisible(empty);
        editorEmptyHint.setManaged(empty);
        if (empty) {
            editorTitle.setText("Editor");
        }
    }

    /** 返回当前激活标签页中的编辑器；若无打开的文件则返回 null。 */
    private CodeEditorPane currentEditor() {
        Tab selected = editorTabs.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getContent() == null) {
            return null;
        }
        return openEditors.get((String) selected.getUserData());
    }

    private static VBox labeledField(String caption, javafx.scene.Node control) {
        Label cap = new Label(caption);
        cap.getStyleClass().add("field-caption");
        return new VBox(2, cap, control);
    }

    private static Label sectionLabel(String text) {
        Label label = new Label(text.toUpperCase());
        label.getStyleClass().add("section-label");
        return label;
    }

    private void browseWorkspace() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select workspace");
        String current = workspaceField.getText().trim();
        if (!current.isBlank()) {
            File dir = new File(current);
            if (dir.isDirectory()) {
                chooser.setInitialDirectory(dir);
            }
        }
        File chosen = chooser.showDialog(root.getScene().getWindow());
        if (chosen != null) {
            workspaceField.setText(chosen.getAbsolutePath());
        }
    }

    private FileTreeBuilder.FileNode selectedFileNode() {
        TreeItem<FileTreeBuilder.FileNode> item = fileTree.getSelectionModel().getSelectedItem();
        return item == null ? null : item.getValue();
    }

    private void applyFileFilter(String filter) {
        fileFilterText = filter == null ? "" : filter.trim().toLowerCase();
        if (allFilePaths == null) {
            return;
        }
        List<String> filtered = allFilePaths.stream()
                .filter(p -> fileFilterText.isEmpty() || p.toLowerCase().contains(fileFilterText))
                .toList();
        Set<String> filteredFiles = allFilePathSet.stream()
                .filter(p -> fileFilterText.isEmpty() || p.toLowerCase().contains(fileFilterText))
                .collect(java.util.stream.Collectors.toSet());
        String rootLabel = Path.of(workspaceField.getText().trim()).getFileName().toString();
        if (rootLabel.isBlank()) {
            rootLabel = "workspace";
        }
        final String treeRootLabel = rootLabel;
        if (!fileFilterText.isEmpty()) {
            for (String path : filtered) {
                expandParentDirs(path);
            }
        }
        snapshotExpandedDirs();
        submitBg(() -> {
            TreeItem<FileTreeBuilder.FileNode> treeRoot =
                    FileTreeBuilder.build(filtered, filteredFiles, treeRootLabel);
            ui(() -> {
                restoreExpandedDirs(treeRoot);
                fileTree.setRoot(treeRoot);
            });
        });
    }

    private void snapshotExpandedDirs() {
        TreeItem<FileTreeBuilder.FileNode> root = fileTree.getRoot();
        if (root == null) {
            return;
        }
        collectExpandedDirs(root);
    }

    private void collectExpandedDirs(TreeItem<FileTreeBuilder.FileNode> node) {
        FileTreeBuilder.FileNode value = node.getValue();
        if (value != null && value.directory() && node.isExpanded() && value.fullPath() != null) {
            expandedDirPaths.add(value.fullPath());
        }
        for (TreeItem<FileTreeBuilder.FileNode> child : node.getChildren()) {
            collectExpandedDirs(child);
        }
    }

    private void restoreExpandedDirs(TreeItem<FileTreeBuilder.FileNode> node) {
        FileTreeBuilder.FileNode value = node.getValue();
        if (value != null && value.directory()) {
            boolean expand = value.fullPath() == null || expandedDirPaths.contains(value.fullPath());
            node.setExpanded(expand);
        }
        for (TreeItem<FileTreeBuilder.FileNode> child : node.getChildren()) {
            restoreExpandedDirs(child);
        }
    }

    private void expandParentDirs(String path) {
        int idx = path.lastIndexOf('/');
        while (idx > 0) {
            expandedDirPaths.add(path.substring(0, idx));
            idx = path.lastIndexOf('/', idx - 1);
        }
    }

    private void refreshHealth() {
        submitBg(() -> {
            try {
                String url = serverField.getText().trim();
                client = new AnvilClient(url.isEmpty() ? settings.serverUrl() : url);
                Map<String, Object> h = client.health();
                ui(() -> {
                    boolean ok = Boolean.TRUE.equals(h.get("ok"));
                    Object scanDepthObj = h.get("workspaceScanMaxDepth");
                    int scanDepth = scanDepthObj instanceof Number n ? n.intValue() : 0;
                    if (ok) {
                        Object ctxTok = h.get("contextCompactThreshold");
                        String ctxHint = ctxTok instanceof Number n ? " · ctx " + (n.intValue() / 1000) + "k" : "";
                        healthLabel.setText(
                                "● v" + h.getOrDefault("protocolVersion", "?") + " · scan " + scanDepth + ctxHint);
                        if (scanDepth < WorkspaceScanner.MIN_EXPECTED_DEPTH) {
                            appendConsole(
                                    ConsoleLine.Kind.ERROR,
                                    "App Server outdated (scan depth "
                                            + scanDepth
                                            + "). Restart: bash scripts/start-ui.sh");
                        }
                    } else {
                        healthLabel.setText("● offline");
                    }
                    healthLabel.getStyleClass().removeAll("ok", "err", "warn");
                    healthLabel.getStyleClass().add(ok ? (scanDepth < WorkspaceScanner.MIN_EXPECTED_DEPTH ? "warn" : "ok") : "err");
                });
            } catch (Exception e) {
                ui(() -> {
                    healthLabel.setText("● offline");
                    healthLabel.getStyleClass().removeAll("ok", "err");
                    healthLabel.getStyleClass().add("err");
                });
            }
        });
    }

    private void connect() {
        final String serverUrl = serverField.getText().trim();
        final String workspacePath = workspaceField.getText().trim();
        final String model = modelBox.getEditor().getText().trim();
        submitBg(() -> {
            settings.setServerUrl(serverUrl);
            settings.setWorkspacePath(workspacePath);
            settings.setDefaultModel(model);
            settings.save();
        });
        titleUpdater.accept(workspacePath.isBlank() ? settings.workspacePath() : workspacePath);
        client = new AnvilClient(serverUrl.isEmpty() ? settings.serverUrl() : serverUrl);
        terminalClient = new TerminalClient(serverUrl.isEmpty() ? settings.serverUrl() : serverUrl);
        if (terminalPanel != null) {
            terminalPanel.setClient(terminalClient);
        }
        setStatus("Connecting…");
        appendConsole(ConsoleLine.Kind.SYSTEM, "connecting…");
        connectBtn.setDisable(true);
        submitBg(() -> {
            try {
                Map<String, Object> thread = client.createThread(
                        workspacePath.isBlank() ? settings.workspacePath() : workspacePath);
                threadId = String.valueOf(thread.get("thread_id"));
                ui(() -> {
                    appendConsole(ConsoleLine.Kind.SYSTEM, "thread " + threadId);
                    setStatus("Connected");
                    threadLabel.setText(threadId);
                    connectBtn.setDisable(false);
                    if (terminalPanel != null) {
                        terminalPanel.connect(threadId);
                    }
                });
                refreshTreeAsync();
                refreshHealth();
            } catch (Exception e) {
                ui(() -> {
                    appendConsole(ConsoleLine.Kind.ERROR, e.getMessage());
                    setStatus("Connect failed");
                    connectBtn.setDisable(false);
                });
            }
        });
    }

    private void refreshTreeAsync() {
        if (running) {
            pendingTreeRefresh = true;
            return;
        }
        doRefreshTreeAsync();
    }

    private void doRefreshTreeAsync() {
        if (closed || client == null || threadId == null) {
            return;
        }
        submitBg(() -> {
            try {
                List<Map<String, Object>> nodes = AnvilClient.nodes(client.workspaceTree(threadId));
                List<String> paths = nodes.stream()
                        .map(n -> String.valueOf(n.get("path")))
                        .sorted(Comparator.naturalOrder())
                        .toList();
                java.util.Set<String> files = nodes.stream()
                        .filter(n -> "file".equals(n.get("type")))
                        .map(n -> String.valueOf(n.get("path")))
                        .collect(java.util.stream.Collectors.toSet());
                ui(() -> {
                    allFilePaths = paths;
                    allFilePathSet = files;
                    applyFileFilter(fileFilterText);
                    long javaSources = files.stream()
                            .filter(p -> p.contains("/src/main/java/") && p.endsWith(".java"))
                            .count();
                    setStatus(files.size() + " files · " + paths.size() + " nodes");
                    if (javaSources == 0 && !files.isEmpty()) {
                        appendConsole(
                                ConsoleLine.Kind.ERROR,
                                "No src/main/java files in tree — old App Server still running?"
                                        + " Run: bash scripts/stop-server.sh && bash scripts/start-ui.sh");
                    }
                });
            } catch (Exception e) {
                ui(() -> appendConsole(ConsoleLine.Kind.ERROR, e.getMessage()));
            }
        });
    }

    private void openFile(String path) {
        if (!isFileOpen(path)) {
            openEditors.remove(path);
            openTabs.remove(path);
            CodeEditorPane editor = new CodeEditorPane();
            editor.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            editor.setOnDirtyChange(dirty -> ui(() -> markEditorDirty(path, dirty)));
            Tab tab = new Tab(tabLabel(path), editor);
            tab.setUserData(path);
            tab.setClosable(true);
            openEditors.put(path, editor);
            openTabs.put(path, tab);
            editorTabs.getTabs().add(tab);
        }
        Tab tab = openTabs.get(path);
        if (tab != null) {
            editorTabs.getSelectionModel().select(tab);
        }
        editorTitle.setText(path);
        updateEditorEmptyHint();
        loadFileAsync(path);
    }

    private boolean isFileOpen(String path) {
        Tab tab = openTabs.get(path);
        return tab != null && editorTabs.getTabs().contains(tab);
    }

    private void unregisterEditorTab(Tab tab) {
        if (tab == null) {
            return;
        }
        Object userData = tab.getUserData();
        if (userData instanceof String path) {
            openEditors.remove(path);
            openTabs.remove(path);
        }
    }

    private void loadFileAsync(String path) {
        loadFileAsync(path, false);
    }

    private void loadFileAsync(String path, boolean force) {
        submitBg(() -> {
            try {
                Map<String, Object> file = client.workspaceFile(threadId, path);
                String content = String.valueOf(file.getOrDefault("content", ""));
                long lineCount = content.isEmpty() ? 0 : content.chars().filter(ch -> ch == '\n').count() + 1;
                CodeEditorPane editor = openEditors.get(path);
                ui(() -> {
                    if (editor == null) {
                        return;
                    }
                    editor.setContent(content, force);
                    editor.resetFind();
                    setStatus(Path.of(path).getFileName() + " · " + lineCount + " lines");
                    if (currentEditor() == editor) {
                        editorTitle.setText(path);
                    }
                });
            } catch (Exception e) {
                ui(() -> appendConsole(ConsoleLine.Kind.ERROR, e.getMessage()));
            }
        });
    }

    private void saveFile(String path, String content, boolean runDiagnostics) {
        if (client == null || threadId == null) {
            appendConsole(ConsoleLine.Kind.ERROR, "not connected");
            return;
        }
        submitBg(() -> {
            try {
                client.saveWorkspaceFile(threadId, path, content);
                ui(() -> {
                    CodeEditorPane editor = openEditors.get(path);
                    if (editor != null) {
                        editor.markSaved();
                    }
                    markEditorDirty(path, false);
                    appendConsole(ConsoleLine.Kind.SYSTEM, "saved " + path);
                    refreshTreeAsync();
                });
                if (runDiagnostics && settings.diagnosticsOnSave()) {
                    List<Map<String, Object>> diags = client.compileDiagnostics(threadId, path);
                    ui(() -> {
                        problemsPanel.setProblems(ProblemsPanel.fromPayload(diags));
                        if (!diags.isEmpty()) {
                            appendConsole(ConsoleLine.Kind.ERROR, diags.size() + " problem(s) after save");
                        }
                    });
                }
            } catch (Exception e) {
                ui(() -> appendConsole(ConsoleLine.Kind.ERROR, "save failed: " + e.getMessage()));
            }
        });
    }

    private Map<String, String> collectUnsavedBuffers() {
        Map<String, String> buffers = new LinkedHashMap<>();
        for (Map.Entry<String, CodeEditorPane> e : openEditors.entrySet()) {
            if (e.getValue().isDirty()) {
                buffers.put(e.getKey(), e.getValue().content());
            }
        }
        return buffers;
    }

    private void markEditorDirty(String path, boolean dirty) {
        if (dirty) {
            dirtyEditorPaths.add(path);
        } else {
            dirtyEditorPaths.remove(path);
        }
        Tab tab = openTabs.get(path);
        if (tab != null) {
            tab.setText(tabLabel(path));
        }
    }

    private static String fileName(String path) {
        int idx = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    private void submitRunInternal() {
        if (running || threadId == null || client == null) {
            return;
        }
        String prompt = promptField.getText().trim();
        if (prompt.isEmpty()) {
            return;
        }
        settings.setDefaultModel(modelBox.getEditor().getText().trim());
        final String promptToSave = prompt;
        final String modelToSave = modelBox.getEditor().getText().trim();
        submitBg(() -> {
            settings.setDefaultModel(modelToSave);
            settings.save();
            settings.addRecentPrompt(promptToSave);
        });
        if (!promptHistory.contains(prompt)) {
            promptHistory.add(0, prompt);
        }
        promptHistoryIndex = -1;
        promptField.clear();
        setRunning(true);
        eventPump.start();
        pendingShellCommands.clear();
        metricsLabel.setText("");
        runDetailPanel.clear();
        appendConsole(ConsoleLine.Kind.USER, prompt);

        submitBg(() -> {
            try {
                List<String> openFiles = new ArrayList<>(openEditors.keySet());
                String focus = null;
                Integer selStart = null;
                Integer selEnd = null;
                String selText = null;
                Tab selected = editorTabs.getSelectionModel().getSelectedItem();
                if (selected != null && selected.getUserData() instanceof String path) {
                    focus = path;
                    CodeEditorPane editor = openEditors.get(path);
                    if (editor != null) {
                        CodeEditorPane.EditorSelectionSnapshot snap = editor.selectionSnapshot();
                        if (snap != null) {
                            selStart = snap.startLine();
                            selEnd = snap.endLine();
                            selText = snap.text();
                        }
                    }
                }
                Map<String, String> editorBuffers = collectUnsavedBuffers();
                Map<String, Object> run = client.startRun(
                        threadId,
                        modeBox.getValue(),
                        modelBox.getEditor().getText().trim(),
                        prompt,
                        profileBox.getValue(),
                        openFiles,
                        focus,
                        selStart,
                        selEnd,
                        selText,
                        autoApproveWritesCheck.isSelected(),
                        editorBuffers);
                currentRunId = String.valueOf(run.get("run_id"));
                ui(() -> {
                    appendConsole(ConsoleLine.Kind.SYSTEM, "run " + currentRunId + " [" + run.get("status") + "]");
                    runLabel.setText(currentRunId);
                });
                sseThread = client.streamEvents(
                        currentRunId,
                        0,
                        eventPump::submit,
                        () -> ui(() -> {
                            eventPump.stop();
                            sseThread = null;
                            finishRunDebounce.playFromStart();
                        }));
            } catch (Exception e) {
                ui(() -> {
                    appendConsole(ConsoleLine.Kind.ERROR, e.getMessage());
                    finishRunDebounce.playFromStart();
                });
            }
        });
    }

    private void cancelRunInternal() {
        if (!running || currentRunId == null || client == null) {
            return;
        }
        String runId = currentRunId;
        appendConsole(ConsoleLine.Kind.SYSTEM, "cancelling " + runId);
        interruptSse();
        submitBg(() -> {
            try {
                client.cancelRun(runId);
            } catch (Exception e) {
                ui(() -> appendConsole(ConsoleLine.Kind.ERROR, e.getMessage()));
            }
        });
    }

    private volatile boolean approvalDialogShowing;
    private final ConcurrentLinkedQueue<Map<String, Object>> pendingApprovals = new ConcurrentLinkedQueue<>();

    private void setRunning(boolean active) {
        running = active;
        runBtn.setDisable(active);
        promptField.setDisable(active);
        runIndicator.setVisible(false);
        runIndicator.setManaged(false);
        runStatusLabel.setText(active ? "● running" : "");
        runStatusLabel.setVisible(active);
        runStatusLabel.setManaged(active);
        cancelBtn.setVisible(active);
        cancelBtn.setManaged(active);
        if (!active) {
            runLabel.setText("");
            currentRunId = null;
        }
    }

    private void handleEvent(Event event) {
        Map<String, Object> payload = event.payload();
        String type = event.type();

        runDetailPanel.onEvent(event);

        if (AgentEventFormatter.showInConsole(type)) {
            if ("message.completed".equals(type)) {
                if (consolePanel.isStreaming()) {
                    consolePanel.finishStreaming();
                } else {
                    appendConsole(ConsoleLine.Kind.MESSAGE, AgentEventFormatter.format(type, payload));
                }
            } else {
                ConsoleLine.Kind kind = AgentEventFormatter.kindFor(type);
                appendConsole(kind, AgentEventFormatter.format(type, payload));
            }
        } else if ("message.completed".equals(type) && consolePanel.isStreaming()) {
            consolePanel.finishStreaming();
        }

        switch (type) {
            case "tool.planned" -> trackShellCommand(payload);
            case "tool.completed" -> mirrorShellIfNeeded(payload);
            case "tool.failed" -> pendingShellCommands.remove(String.valueOf(payload.get("tool_call_id")));
            case "edit.preview" -> showEditPreview(payload);
            case "edit.summary" -> onEditSummary(payload);
            case "approval.required" -> {
                runStatusLabel.setText("● waiting approval");
                showApproval(payload);
            }
            case "approval.resolved" -> {
                if (running) {
                    runStatusLabel.setText("● running");
                }
            }
            case "run.completed" -> {
                setStatus("Run completed");
                updateMetricsFromPayload(payload.get("usage"));
            }
            case "model.completed" -> updateMetricsFromPayload(payload.get("usage_total"));
            case "run.failed", "run.cancelled" -> setStatus(type);
            default -> {}
        }
    }

    @SuppressWarnings("unchecked")
    private void updateMetricsFromPayload(Object usageObj) {
        if (usageObj instanceof Map<?, ?> map) {
            updateMetrics((Map<String, Object>) map);
        }
    }

    private void updateMetrics(Map<String, Object> usage) {
        String summary = AgentEventFormatter.metricsSummary(usage);
        metricsLabel.setText(summary.isBlank() ? "" : summary);
    }

    private void trackShellCommand(Map<String, Object> payload) {
        if (!settings.mirrorAgentShell()) {
            return;
        }
        if (!"shell.exec".equals(String.valueOf(payload.get("name")))) {
            return;
        }
        Object argsObj = payload.get("arguments");
        if (!(argsObj instanceof Map<?, ?> args)) {
            return;
        }
        Object command = args.get("command");
        if (command == null) {
            return;
        }
        pendingShellCommands.put(String.valueOf(payload.get("tool_call_id")), String.valueOf(command));
    }

    private void mirrorShellIfNeeded(Map<String, Object> payload) {
        if (!settings.mirrorAgentShell() || terminalPanel == null) {
            return;
        }
        String toolCallId = String.valueOf(payload.get("tool_call_id"));
        String command = pendingShellCommands.remove(toolCallId);
        if (command == null) {
            return;
        }
        Object preview = payload.get("preview");
        terminalPanel.mirrorShellExec(command, preview != null ? String.valueOf(preview) : "");
    }

    private void showEditPreview(Map<String, Object> payload) {
        String path = str(payload.get("path"), "");
        if (path.isBlank()) {
            return;
        }
        String previous = payload.get("previous_content") != null ? String.valueOf(payload.get("previous_content")) : "";
        Object newContentObj = payload.get("new_content");
        ensureFileTab(path);
        if (centerTabs != null && diffTab != null) {
            centerTabs.getSelectionModel().select(diffTab);
            updateDiffTabTitle();
        }
        markFileModified(path, true);
        var pending = new DiffReviewPanel.PendingEdit(path, previous, "");
        diffReviewPanel.showLoading(pending);
        submitBg(() -> {
            String after;
            if (newContentObj != null) {
                after = String.valueOf(newContentObj);
            } else {
                after = readWorkspaceFile(path);
            }
            if (previous.isEmpty() && newContentObj == null && payload.get("previous_content") == null) {
                Object diffText = payload.get("diff");
                if (diffText != null && !String.valueOf(diffText).isBlank()) {
                    ui(() -> {
                        diffReviewPanel.showDiffText(path, String.valueOf(diffText));
                        updateDiffTabTitle();
                        loadFileAsync(path, true);
                    });
                    return;
                }
            }
            List<DiffReviewPanel.DiffRow> rows = DiffEngine.lines(previous, after);
            var edit = new DiffReviewPanel.PendingEdit(path, previous, after);
            ui(() -> {
                diffReviewPanel.show(edit, rows);
                updateDiffTabTitle();
                loadFileAsync(path, true);
            });
        });
    }

    private void updateDiffTabTitle() {
        if (diffTab == null) {
            return;
        }
        int n = diffReviewPanel.pendingCount();
        diffTab.setText(n == 0 ? "Review Diff" : "Review Diff (" + n + ")");
    }

    private void onDiffQueueEmpty() {
        if (centerTabs != null && codeTab != null) {
            centerTabs.getSelectionModel().select(codeTab);
        }
        updateDiffTabTitle();
    }

    private String readWorkspaceFile(String relativePath) {
        String workspace = workspaceField.getText().trim();
        if (workspace.isBlank()) {
            return "";
        }
        try {
            Path file = Path.of(workspace).resolve(relativePath).normalize();
            return Files.isRegularFile(file) ? Files.readString(file) : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private void onEditSummary(Map<String, Object> payload) {
        String path = str(payload.get("path"), "");
        if (path.isBlank()) {
            return;
        }
        markFileModified(path, true);
        refreshTreeAsync();
    }

    private void applyHunkDecision(DiffReviewPanel.HunkDecisionEvent event) {
        if (event == null || event.path() == null) {
            return;
        }
        DiffReviewPanel.PendingEdit edit = diffReviewPanel.pendingEditFor(event.path());
        if (edit == null) {
            return;
        }
        var hunks = diffReviewPanel.hunksFor(event.path());
        var rows = diffReviewPanel.rowsFor(event.path());
        String content = DiffEngine.rebuild(edit.previousContent(), edit.newContent(), rows, hunks);
        String workspace = workspaceField.getText().trim();
        if (workspace.isBlank()) {
            return;
        }
        submitBg(() -> {
            try {
                Path file = Path.of(workspace).resolve(event.path()).normalize();
                Files.writeString(file, content);
                ui(() -> {
                    appendConsole(
                            ConsoleLine.Kind.SYSTEM,
                            "hunk " + (event.hunkIndex() + 1) + " "
                                    + event.decision().name().toLowerCase()
                                    + " · "
                                    + event.path());
                    if (isFileOpen(event.path())) {
                        loadFileAsync(event.path(), true);
                    }
                });
            } catch (Exception e) {
                ui(() -> appendConsole(ConsoleLine.Kind.ERROR, "hunk apply failed: " + e.getMessage()));
            }
        });
    }

    private void acceptEdit(DiffReviewPanel.PendingEdit edit) {
        if (edit == null) {
            return;
        }
        appendConsole(ConsoleLine.Kind.SYSTEM, "edit accepted · " + edit.path());
        markFileModified(edit.path(), false);
        openFile(edit.path());
        updateDiffTabTitle();
        if (diffReviewPanel.isEmpty()) {
            if (centerTabs != null && codeTab != null) {
                centerTabs.getSelectionModel().select(codeTab);
            }
        }
        refreshTreeAsync();
    }

    private void ensureFileTab(String path) {
        if (!isFileOpen(path)) {
            CodeEditorPane editor = new CodeEditorPane();
            editor.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            editor.setOnDirtyChange(dirty -> ui(() -> markEditorDirty(path, dirty)));
            Tab tab = new Tab(tabLabel(path), editor);
            tab.setUserData(path);
            tab.setClosable(true);
            openEditors.put(path, editor);
            openTabs.put(path, tab);
            editorTabs.getTabs().add(tab);
        }
        editorTitle.setText(path);
        updateEditorEmptyHint();
    }

    private void markFileModified(String path, boolean modified) {
        if (modified) {
            modifiedFilePaths.add(path);
        } else {
            modifiedFilePaths.remove(path);
        }
        Tab tab = openTabs.get(path);
        if (tab != null) {
            tab.setText(tabLabel(path));
        }
    }

    private String tabLabel(String path) {
        String name = fileName(path);
        boolean agentPending = modifiedFilePaths.contains(path);
        boolean dirty = dirtyEditorPaths.contains(path);
        if (dirty && agentPending) {
            return name + " ●*";
        }
        if (dirty) {
            return name + " ●";
        }
        if (agentPending) {
            return name + " *";
        }
        return name;
    }

    private static int intPayload(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void revertEdit(DiffReviewPanel.PendingEdit edit) {
        if (edit.previousContent() == null) {
            appendConsole(ConsoleLine.Kind.ERROR, "cannot revert (previous content not available)");
            return;
        }
        String workspace = workspaceField.getText().trim();
        if (workspace.isBlank()) {
            appendConsole(ConsoleLine.Kind.ERROR, "cannot revert (no workspace)");
            return;
        }
        submitBg(() -> {
            try {
                Path file = Path.of(workspace).resolve(edit.path()).normalize();
                Files.writeString(file, edit.previousContent());
                ui(() -> {
                    appendConsole(ConsoleLine.Kind.SYSTEM, "reverted " + edit.path());
                    markFileModified(edit.path(), false);
                    updateDiffTabTitle();
                    if (diffReviewPanel.isEmpty()) {
                        if (centerTabs != null && codeTab != null) {
                            centerTabs.getSelectionModel().select(codeTab);
                        }
                    }
                    if (isFileOpen(edit.path())) {
                        loadFileAsync(edit.path(), true);
                    }
                    refreshTreeAsync();
                });
            } catch (Exception e) {
                ui(() -> appendConsole(ConsoleLine.Kind.ERROR, "revert failed: " + e.getMessage()));
            }
        });
    }

    private void showApproval(Map<String, Object> payload) {
        pendingApprovals.offer(payload);
        drainApprovalQueue();
    }

    private void drainApprovalQueue() {
        if (approvalDialogShowing) {
            return;
        }
        Map<String, Object> payload = pendingApprovals.poll();
        if (payload == null) {
            return;
        }
        String id = str(payload.get("approval_id"), "");
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Approval · Window #" + windowId);
        alert.setHeaderText(str(payload.get("tool"), "tool") + "  ·  risk: " + str(payload.get("risk"), "?"));
        alert.setContentText(formatApprovalPreview(payload));
        alert.getDialogPane().getStylesheets().add(WorkbenchWindow.class.getResource("/anvil.css").toExternalForm());

        ButtonType allowOnce = new ButtonType("Allow Once", ButtonBar.ButtonData.OK_DONE);
        ButtonType allowSession = new ButtonType("Allow Session");
        ButtonType deny = new ButtonType("Deny", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType alwaysDeny = new ButtonType("Always Deny");
        alert.getButtonTypes().setAll(deny, alwaysDeny, allowSession, allowOnce);

        approvalDialogShowing = true;
        alert.setOnHidden(ev -> {
            approvalDialogShowing = false;
            ButtonType choice = alert.getResult();
            String decision =
                    choice == null
                            ? ApprovalDecision.DENY.wireValue()
                            : switch (choice.getText()) {
                                case "Allow Once" -> ApprovalDecision.ALLOW_ONCE.wireValue();
                                case "Allow Session" -> ApprovalDecision.ALLOW_SESSION.wireValue();
                                case "Always Deny" -> ApprovalDecision.ALWAYS_DENY.wireValue();
                                default -> ApprovalDecision.DENY.wireValue();
                            };
            submitBg(() -> {
                try {
                    client.respondApproval(id, decision);
                    ui(() -> {
                        appendConsole(ConsoleLine.Kind.APPROVAL, id + " → " + decision);
                        drainApprovalQueue();
                    });
                } catch (Exception e) {
                    ui(() -> {
                        appendConsole(ConsoleLine.Kind.ERROR, e.getMessage());
                        drainApprovalQueue();
                    });
                }
            });
        });
        alert.show();
    }

    @SuppressWarnings("unchecked")
    private static String formatApprovalPreview(Map<String, Object> payload) {
        Object previewObj = payload.get("preview");
        if (previewObj instanceof Map<?, ?> preview) {
            StringBuilder sb = new StringBuilder();
            Object summary = preview.get("summary");
            if (summary != null && !String.valueOf(summary).isBlank()) {
                sb.append(summary);
            }
            Object paths = preview.get("paths");
            if (paths instanceof List<?> list && !list.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append("Paths:");
                for (Object path : list) {
                    sb.append("\n  • ").append(path);
                }
            }
            Object command = preview.get("command");
            if (command != null && !String.valueOf(command).isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append("Command: ").append(command);
            }
            if (!sb.isEmpty()) {
                return sb.toString();
            }
        }
        Object summary = payload.get("summary");
        return summary != null ? String.valueOf(summary) : "No preview provided.";
    }

    private void finishRunDeferred() {
        if (closed) {
            return;
        }
        eventPump.stop();
        runDetailPanel.flushPendingRows();
        setRunning(false);
        pendingTreeRefresh = false;
        doRefreshTreeAsync();
        FileTreeBuilder.FileNode selected = selectedFileNode();
        if (selected != null && selected.fullPath() != null && !selected.directory()) {
            openFile(selected.fullPath());
        }
    }

    private void setStatus(String text) {
        statusLabel.setText("#" + windowId + " · " + text);
    }

    private void appendConsole(ConsoleLine.Kind kind, String text) {
        consolePanel.append(kind, text);
    }

    private static String str(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static void ui(Runnable action) {
        Platform.runLater(action);
    }
}
