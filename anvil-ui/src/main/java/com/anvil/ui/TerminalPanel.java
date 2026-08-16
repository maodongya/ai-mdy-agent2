package com.anvil.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 终端面板（M2）：多 Tab 终端会话。
 *
 * <p>每个 Tab 展示一个会话的输出区 + 命令输入行 + Stop 按钮；支持新建（+）与关闭。
 * 输出在 UI 线程追加，ANSI 控制码剥离为纯文本。</p>
 */
final class TerminalPanel extends VBox {

    interface Listener {
        TerminalClient client();

        String currentThreadId();

        void onStatusChanged();
    }

    private final Listener listener;
    private final TabPane tabs = new TabPane();
    private final Button addBtn = new Button("+");
    private final Map<String, TerminalTab> bySessionId = new java.util.HashMap<>();
    private int tabCounter;

    TerminalPanel(Listener listener) {
        super(4);
        this.listener = listener;
        getStyleClass().add("terminal-panel");
        tabs.getStyleClass().add("editor-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        addBtn.getStyleClass().add("icon-btn");
        addBtn.setTooltip(new Tooltip("New terminal"));
        addBtn.setOnAction(e -> newTerminal());

        HBox header = new HBox(6, sectionLabel("Terminal"), new javafx.scene.layout.Region(), addBtn);
        HBox.setHgrow(header.getChildren().get(1), Priority.ALWAYS);
        header.setAlignment(Pos.CENTER_LEFT);

        setPadding(new Insets(4, 8, 4, 8));
        getChildren().addAll(header, tabs);
    }

    void connect() {
        if (tabs.getTabs().isEmpty()) {
            newTerminal();
        }
    }

    /** 新建终端会话并创建 Tab。 */
    void newTerminal() {
        TerminalClient client = listener.client();
        String threadId = listener.currentThreadId();
        if (client == null || threadId == null || threadId.isBlank()) {
            return;
        }
        submitBg(() -> {
            try {
                TerminalClient.SessionInfo info = client.createSession(threadId, "bash");
                javafx.application.Platform.runLater(() -> addTab(info));
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> System.err.println("terminal create failed: " + e.getMessage()));
            }
        });
    }

    /** 追加 agent 镜像行到当前终端（M4）。 */
    void mirrorShellExec(String command, String output) {
        TerminalTab active = activeTab();
        if (active == null && tabs.getTabs().isEmpty()) {
            connect();
            active = activeTab();
        }
        if (active != null) {
            active.append("⚙ " + command);
            if (output != null && !output.isBlank()) {
                for (String line : output.split("\n", -1)) {
                    if (!line.isEmpty()) {
                        active.append(line);
                    }
                }
            }
        }
    }

    void focusInput() {
        TerminalTab active = activeTab();
        if (active != null) {
            active.focusInput();
            tabs.getSelectionModel().select(active.tab);
            return;
        }
        if (tabs.getTabs().isEmpty()) {
            newTerminal();
            javafx.application.Platform.runLater(() -> {
                TerminalTab created = activeTab();
                if (created != null) {
                    created.focusInput();
                    tabs.getSelectionModel().select(created.tab);
                }
            });
        }
    }

    void nextTab() {
        if (tabs.getTabs().size() <= 1) {
            return;
        }
        int idx = tabs.getSelectionModel().getSelectedIndex();
        tabs.getSelectionModel().select((idx + 1) % tabs.getTabs().size());
    }

    private void addTab(TerminalClient.SessionInfo info) {
        TerminalTab tabContent = new TerminalTab(this, info);
        bySessionId.put(info.sessionId(), tabContent);
        Tab t = new Tab("Terminal " + (++tabCounter), tabContent);
        tabContent.tab = t;
        t.setClosable(true);
        t.setOnClosed(ev -> {
            bySessionId.remove(info.sessionId());
            tabContent.closeStream();
        });
        tabs.getTabs().add(t);
        tabs.getSelectionModel().select(t);
        tabContent.startStream(0);
    }

    private TerminalTab activeTab() {
        Tab selected = tabs.getSelectionModel().getSelectedItem();
        if (selected == null || !(selected.getContent() instanceof TerminalTab tab)) {
            return null;
        }
        return tab;
    }

    @SuppressWarnings("unchecked")
    private void handleEvent(Map<String, Object> params) {
        String sessionId = String.valueOf(params.get("session_id"));
        String type = String.valueOf(params.get("type"));
        long seq = params.get("seq") instanceof Number n ? n.longValue() : 0;
        TerminalTab tab = bySessionId.get(sessionId);
        if (tab == null) {
            return;
        }
        tab.lastSeq = Math.max(tab.lastSeq, seq);
        Object payload = params.get("payload");
        Map<String, Object> pl = payload instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        switch (type) {
            case "terminal.output" -> {
                Object lines = pl.get("lines");
                if (lines instanceof List<?> l) {
                    for (Object line : l) {
                        tab.append(String.valueOf(line));
                    }
                }
            }
            case "terminal.job_start" -> tab.setRunning(true);
            case "terminal.job_done" -> {
                tab.setRunning(false);
                tab.setExitCode(String.valueOf(pl.get("exit_code")));
                Object ms = pl.get("duration_ms");
                tab.append("[exit " + pl.get("exit_code") + (ms != null ? " · " + ms + "ms" : "") + "]");
            }
            case "terminal.error" -> {
                tab.setRunning(false);
                tab.append("[error] " + pl.get("message"));
            }
            case "terminal.status" -> {
                String status = String.valueOf(pl.get("status"));
                tab.setStatus(status);
                tab.setRunning("RUNNING".equals(status));
            }
            default -> {}
        }
    }

    private static void submitBg(Runnable task) {
        Thread t = new Thread(task, "terminal-bg");
        t.setDaemon(true);
        t.start();
    }

    private static Label sectionLabel(String text) {
        Label label = new Label(text.toUpperCase());
        label.getStyleClass().add("section-label");
        return label;
    }

    /** 单个终端 Tab。 */
    static final class TerminalTab extends VBox {
        final TerminalPanel panel;
        final String sessionId;
        final String cwd;
        Tab tab;
        long lastSeq;
        volatile Thread streamThread;

        final ObservableList<String> output = FXCollections.observableArrayList();
        final ListView<String> list = new ListView<>(output);
        final TextField input = new TextField();
        final Button stopBtn = new Button("Stop");
        final Label statusLabel = new Label("IDLE");
        final Label cwdLabel = new Label();
        final Label exitLabel = new Label();
        final List<String> commandHistory = new ArrayList<>();
        int historyIndex = -1;

        TerminalTab(TerminalPanel panel, TerminalClient.SessionInfo info) {
            this.panel = panel;
            this.sessionId = info.sessionId();
            this.cwd = info.cwd();

            list.setEditable(false);
            list.setFocusTraversable(false);
            list.getStyleClass().add("console-list");
            VBox.setVgrow(list, Priority.ALWAYS);

            input.getStyleClass().add("terminal-input");
            input.setPromptText("$ command…");
            input.setOnAction(e -> runCommand());
            input.addEventHandler(KeyEvent.KEY_PRESSED, this::handleHistoryKey);

            stopBtn.getStyleClass().add("danger-btn");
            stopBtn.setVisible(false);
            stopBtn.setManaged(false);
            stopBtn.setOnAction(e -> {
                panel.listener.client().stop(sessionId);
                append("⏹ stopped");
            });

            statusLabel.getStyleClass().add("terminal-status");
            cwdLabel.getStyleClass().add("terminal-meta");
            cwdLabel.setText(shortCwd(cwd));
            cwdLabel.setTooltip(new Tooltip(cwd));
            exitLabel.getStyleClass().add("terminal-meta");

            HBox inputRow = new HBox(6, input, stopBtn);
            HBox.setHgrow(input, Priority.ALWAYS);
            inputRow.setAlignment(Pos.CENTER_LEFT);

            HBox footer = new HBox(12, statusLabel, cwdLabel, exitLabel);
            footer.setAlignment(Pos.CENTER_LEFT);
            footer.getStyleClass().add("terminal-footer");

            getChildren().addAll(list, inputRow, footer);
            VBox.setVgrow(list, Priority.ALWAYS);
            setStatus(info.status());
        }

        void startStream(long fromSeq) {
            closeStream();
            TerminalClient client = panel.listener.client();
            if (client == null) {
                return;
            }
            long cursor = fromSeq > 0 ? fromSeq : lastSeq;
            streamThread = client.stream(sessionId, cursor, panel::handleEvent);
            streamThread.setUncaughtExceptionHandler((t, e) -> reconnectLater());
        }

        private void reconnectLater() {
            if (tab == null || !panel.bySessionId.containsKey(sessionId)) {
                return;
            }
            javafx.application.Platform.runLater(() -> {
                Thread t = new Thread(() -> {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (panel.bySessionId.containsKey(sessionId)) {
                        startStream(lastSeq);
                    }
                }, "term-reconnect-" + sessionId);
                t.setDaemon(true);
                t.start();
            });
        }

        void closeStream() {
            Thread t = streamThread;
            if (t != null) {
                t.interrupt();
            }
            streamThread = null;
        }

        void runCommand() {
            String cmd = input.getText().trim();
            if (cmd.isBlank()) {
                return;
            }
            if (!commandHistory.isEmpty() && commandHistory.get(0).equals(cmd)) {
                // keep
            } else {
                commandHistory.add(0, cmd);
                while (commandHistory.size() > 50) {
                    commandHistory.remove(commandHistory.size() - 1);
                }
            }
            historyIndex = -1;
            input.clear();
            append("$ " + cmd);
            try {
                panel.listener.client().exec(sessionId, cmd);
                setRunning(true);
            } catch (Exception ex) {
                append("[error] " + ex.getMessage());
            }
        }

        private void handleHistoryKey(KeyEvent e) {
            if (commandHistory.isEmpty()) {
                return;
            }
            if (e.getCode() == KeyCode.UP) {
                if (historyIndex < commandHistory.size() - 1) {
                    historyIndex++;
                }
                input.setText(commandHistory.get(historyIndex));
                input.positionCaret(input.getText().length());
                e.consume();
            } else if (e.getCode() == KeyCode.DOWN) {
                if (historyIndex > 0) {
                    historyIndex--;
                    input.setText(commandHistory.get(historyIndex));
                } else {
                    historyIndex = -1;
                    input.clear();
                }
                e.consume();
            }
        }

        void append(String text) {
            String line = AnsiStrip.plain(text);
            if (javafx.application.Platform.isFxApplicationThread()) {
                output.add(line);
                list.scrollTo(output.size() - 1);
            } else {
                javafx.application.Platform.runLater(() -> {
                    output.add(line);
                    list.scrollTo(output.size() - 1);
                });
            }
        }

        void setStatus(String s) {
            javafx.application.Platform.runLater(() -> statusLabel.setText(s));
        }

        void setExitCode(String code) {
            javafx.application.Platform.runLater(() -> exitLabel.setText(code == null || "null".equals(code) ? "" : "exit " + code));
        }

        void setRunning(boolean running) {
            javafx.application.Platform.runLater(() -> {
                stopBtn.setVisible(running);
                stopBtn.setManaged(running);
                if (running) {
                    statusLabel.setText("RUNNING");
                }
            });
        }

        void focusInput() {
            javafx.application.Platform.runLater(input::requestFocus);
        }

        private static String shortCwd(String path) {
            if (path == null || path.isBlank()) {
                return "";
            }
            if (path.length() <= 48) {
                return path;
            }
            return "…" + path.substring(path.length() - 45);
        }
    }
}
