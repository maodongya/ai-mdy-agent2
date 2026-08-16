package com.anvil.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;

public final class UiSettings {

    private static final Preferences PREFS = Preferences.userRoot().node("com.anvil.ui");
    private static final int MAX_RECENT = 20;

    private String serverUrl;
    private String workspacePath;
    private String defaultModel;
    private boolean autoApproveWrites;
    private double mainDivider;
    private double centerDivider;
    private double terminalDivider;
    private boolean terminalVisible;
    private boolean mirrorAgentShell;
    private String theme = "dark";

    public UiSettings(
            String serverUrl,
            String workspacePath,
            String defaultModel,
            boolean autoApproveWrites,
            double mainDivider,
            double centerDivider,
            double terminalDivider,
            boolean terminalVisible,
            boolean mirrorAgentShell) {
        this.serverUrl = serverUrl;
        this.workspacePath = workspacePath;
        this.defaultModel = defaultModel;
        this.autoApproveWrites = autoApproveWrites;
        this.mainDivider = mainDivider;
        this.centerDivider = centerDivider;
        this.terminalDivider = terminalDivider;
        this.terminalVisible = terminalVisible;
        this.mirrorAgentShell = mirrorAgentShell;
    }

    public static UiSettings load() {
        UiSettings s = new UiSettings(
                PREFS.get("serverUrl", "http://127.0.0.1:7788"),
                PREFS.get("workspacePath", defaultWorkspace()),
                PREFS.get("defaultModel", "deepseek:deepseek-chat"),
                PREFS.getBoolean("autoApproveWrites", false),
                PREFS.getDouble("mainDivider", 0.21),
                PREFS.getDouble("centerDivider", 0.56),
                PREFS.getDouble("terminalDivider", 0.72),
                PREFS.getBoolean("terminalVisible", true),
                PREFS.getBoolean("mirrorAgentShell", true));
        s.theme = PREFS.get("theme", "dark");
        return s;
    }

    public void save() {
        PREFS.put("serverUrl", serverUrl);
        PREFS.put("workspacePath", workspacePath);
        PREFS.put("defaultModel", defaultModel);
        PREFS.putBoolean("autoApproveWrites", autoApproveWrites);
        PREFS.putDouble("mainDivider", mainDivider);
        PREFS.putDouble("centerDivider", centerDivider);
        PREFS.putDouble("terminalDivider", terminalDivider);
        PREFS.putBoolean("terminalVisible", terminalVisible);
        PREFS.putBoolean("mirrorAgentShell", mirrorAgentShell);
        PREFS.put("theme", theme);
    }

    public void saveDividers(double main, double center) {
        this.mainDivider = main;
        this.centerDivider = center;
        PREFS.putDouble("mainDivider", main);
        PREFS.putDouble("centerDivider", center);
    }

    public void saveTerminalDivider(double terminal) {
        this.terminalDivider = terminal;
        PREFS.putDouble("terminalDivider", terminal);
    }

    public void addRecentPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return;
        }
        List<String> recent = new ArrayList<>(recentPrompts());
        recent.removeIf(prompt::equals);
        recent.add(0, prompt);
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }
        PREFS.put("recentPrompts", String.join("\u0001", recent));
    }

    public List<String> recentPrompts() {
        String raw = PREFS.get("recentPrompts", "");
        if (raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("\u0001", -1)).filter(s -> !s.isBlank()).toList();
    }

    public static String defaultWorkspace() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.isRegularFile(cwd.resolve("pom.xml")) && Files.isDirectory(cwd.resolve("anvil-ui"))) {
            return cwd.toString();
        }
        Path candidate = cwd.resolve("fixtures/repos/sample-lib");
        if (Files.isDirectory(candidate)) {
            return candidate.toString();
        }
        Path parent = cwd.getParent();
        if (parent != null) {
            Path alt = parent.resolve("fixtures/repos/sample-lib");
            if (Files.isDirectory(alt)) {
                return alt.toAbsolutePath().normalize().toString();
            }
        }
        return cwd.toString();
    }

    public String serverUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String workspacePath() {
        return workspacePath;
    }

    public void setWorkspacePath(String workspacePath) {
        this.workspacePath = workspacePath;
    }

    public String defaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public boolean autoApproveWrites() {
        return autoApproveWrites;
    }

    public void setAutoApproveWrites(boolean autoApproveWrites) {
        this.autoApproveWrites = autoApproveWrites;
    }

    public double mainDivider() {
        return mainDivider;
    }

    public double centerDivider() {
        return centerDivider;
    }

    public double terminalDivider() {
        return terminalDivider;
    }

    public boolean terminalVisible() {
        return terminalVisible;
    }

    public void setTerminalVisible(boolean terminalVisible) {
        this.terminalVisible = terminalVisible;
    }

    public boolean mirrorAgentShell() {
        return mirrorAgentShell;
    }

    public void setMirrorAgentShell(boolean mirrorAgentShell) {
        this.mirrorAgentShell = mirrorAgentShell;
    }

    public String theme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }
}
