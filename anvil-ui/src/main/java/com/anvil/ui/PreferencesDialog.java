package com.anvil.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

final class PreferencesDialog {

    private PreferencesDialog() {}

    static boolean show(UiSettings settings, javafx.stage.Window owner) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Preferences");
        dialog.setHeaderText("Default connection settings");
        dialog.initOwner(owner);
        dialog.getDialogPane().getStylesheets().add(WorkbenchWindow.class.getResource("/anvil.css").toExternalForm());

        TextField server = new TextField(settings.serverUrl());
        TextField workspace = new TextField(settings.workspacePath());
        ComboBox<String> model = new ComboBox<>();
        model.getItems().addAll(ModelPresets.all());
        model.setEditable(true);
        model.setValue(settings.defaultModel());

        CheckBox yoloWrites = new CheckBox("Auto-approve fs.write (Yolo)");
        yoloWrites.setSelected(settings.autoApproveWrites());
        yoloWrites.setTooltip(new Tooltip("Skip approval dialogs for fs.write in Agent mode"));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.add(new Label("Server URL"), 0, 0);
        grid.add(server, 1, 0);
        grid.add(new Label("Default workspace"), 0, 1);
        grid.add(workspace, 1, 1);
        grid.add(new Label("Default model"), 0, 2);
        grid.add(model, 1, 2);
        grid.add(yoloWrites, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        var result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return false;
        }
        settings.setServerUrl(server.getText().trim());
        settings.setWorkspacePath(workspace.getText().trim());
        settings.setDefaultModel(model.getEditor().getText().trim());
        settings.setAutoApproveWrites(yoloWrites.isSelected());
        settings.save();
        return true;
    }
}
