package com.anvil.ui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * 会话终端面板（M2/M3）：接入 TerminalClient + TerminalPanel，
 * 将底部终端区域与工作区绑定。
 */
final class SessionTerminalPanel extends VBox implements TerminalPanel.Listener {

    private final TerminalPanel terminalPanel;
    private TerminalClient client;
    private final ReadOnlyStringWrapper threadId = new ReadOnlyStringWrapper("");
    private Runnable onStatusChanged = () -> {};

    SessionTerminalPanel(SplitPane parent, TerminalClient client) {
        super(4);
        this.client = client;
        terminalPanel = new TerminalPanel(this);
        terminalPanel.setPrefHeight(200);
        terminalPanel.setMinHeight(120);
        VBox.setVgrow(terminalPanel, Priority.ALWAYS);
        getChildren().add(terminalPanel);
        setAlignment(Pos.CENTER_LEFT);
    }

    void setClient(TerminalClient client) {
        this.client = client;
    }

    /** 由 WorkbenchView 在连接建立后调用，设置当前线程 id。 */
    void setThreadId(String threadId) {
        this.threadId.set(threadId == null ? "" : threadId);
    }

    @Override
    public TerminalClient client() {
        return client;
    }

    @Override
    public String currentThreadId() {
        return threadId.get();
    }

    @Override
    public void onStatusChanged() {
        onStatusChanged.run();
    }

    void setOnStatusChanged(Runnable r) {
        this.onStatusChanged = r == null ? () -> {} : r;
    }

    void connect(String threadId) {
        setThreadId(threadId);
        if (threadId != null && !threadId.isBlank()) {
            terminalPanel.connect();
        }
    }

    void focusInput() {
        terminalPanel.focusInput();
    }

    void mirrorShellExec(String command, String output) {
        terminalPanel.mirrorShellExec(command, output);
    }

    void nextTab() {
        terminalPanel.nextTab();
    }
}
