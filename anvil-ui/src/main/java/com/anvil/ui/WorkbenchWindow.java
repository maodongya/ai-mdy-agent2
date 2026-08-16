package com.anvil.ui;

import java.nio.file.Path;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCombination;

public final class WorkbenchWindow {

    private static int windowCounter;

    private final int windowId;
    private final WorkbenchView view;
    private final javafx.stage.Stage stage;
    private final UiSettings settings;

    private WorkbenchWindow(UiSettings settings) {
        this.settings = settings;
        this.windowId = ++windowCounter;
        this.view = new WorkbenchView(settings, windowId, this::updateTitle);
        var scene = new javafx.scene.Scene(view.getRoot(), 1320, 860);
        scene.getStylesheets().add(WorkbenchWindow.class.getResource("/anvil.css").toExternalForm());
        AnvilThemes.apply(scene, settings.theme());
        view.registerShortcuts(scene);

        stage = new javafx.stage.Stage();
        stage.setScene(scene);
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        updateTitle(settings.workspacePath());
        stage.setOnCloseRequest(e -> view.shutdown());

        view.setMenuBar(buildMenuBar());
    }

    public static WorkbenchWindow open(UiSettings settings) {
        WorkbenchWindow window = new WorkbenchWindow(settings);
        window.stage.show();
        window.view.connectOnStartup();
        return window;
    }

    public static WorkbenchWindow openNew() {
        return open(UiSettings.load());
    }

    private void updateTitle(String workspacePath) {
        String shortPath = workspacePath == null || workspacePath.isBlank()
                ? "disconnected"
                : Path.of(workspacePath).getFileName().toString();
        stage.setTitle("Anvil #" + windowId + " — " + shortPath);
    }

    private javafx.scene.control.MenuBar buildMenuBar() {
        var file = new javafx.scene.control.Menu("File");
        var save = menu("Save", "Shortcut+S", view::saveCurrentFile);
        var newWindow = menu("New Window", "Shortcut+N", () -> openNew());
        var prefs = menu("Preferences…", "Shortcut+Comma", view::showPreferences);
        var close = menu("Close Window", "Shortcut+W", stage::close);
        file.getItems().addAll(save, newWindow, prefs, new javafx.scene.control.SeparatorMenuItem(), close);

        var edit = new javafx.scene.control.Menu("Edit");
        edit.getItems()
                .addAll(
                        menu("Go to Definition", "F12", view::goToDefinition),
                        menu("Find References", "Shift+F12", view::findReferences));

        var agent = new javafx.scene.control.Menu("Agent");
        agent.getItems()
                .addAll(
                        menu("Run", "Shortcut+Enter", view::submitRun),
                        menu("Stop", "Shortcut+.", view::cancelRun));

        var viewMenu = new javafx.scene.control.Menu("View");
        viewMenu.getItems()
                .addAll(
                        menu("Find in File", "Shortcut+F", view::showFindBar),
                        menu("Refresh Files", "Shortcut+R", view::refreshTree),
                        menu("Clear Console", "Shortcut+K", view::clearConsole),
                        menu("Copy Console", "Shortcut+Shift+C", view::copyConsole),
                        new SeparatorMenuItem(),
                        menu("Toggle Terminal", "Ctrl+BACK_QUOTE", view::toggleTerminal),
                        menu("Focus Terminal", "Ctrl+Shift+5", view::focusTerminalInput));

        var bar = new javafx.scene.control.MenuBar(file, edit, agent, viewMenu, buildThemeMenu());
        bar.getStyleClass().add("menu-bar");
        return bar;
    }

    /** 主题切换菜单（Theme）：Dark / Light / Monokai，统一整套编辑器风格并持久化。 */
    private Menu buildThemeMenu() {
        var themeMenu = new Menu("Theme");
        var group = new ToggleGroup();
        for (AnvilThemes.THEME t : AnvilThemes.THEMES) {
            var item = new RadioMenuItem(t.label());
            item.setToggleGroup(group);
            item.setSelected(t.id().equals(settings.theme()));
            item.setOnAction(e -> {
                if (item.isSelected()) {
                    settings.setTheme(t.id());
                    settings.save();
                    AnvilThemes.apply(stage.getScene(), t.id());
                }
            });
            themeMenu.getItems().add(item);
        }
        return themeMenu;
    }

    private static javafx.scene.control.MenuItem menu(String text, String accel, Runnable action) {
        var item = new javafx.scene.control.MenuItem(text);
        if (accel != null && !accel.isBlank()) {
            item.setAccelerator(KeyCombination.valueOf(accel));
        }
        item.setOnAction(e -> action.run());
        return item;
    }
}
