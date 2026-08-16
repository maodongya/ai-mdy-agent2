package com.anvil.ui;

import javafx.application.Application;

public class AnvilUiApp extends Application {

    @Override
    public void start(javafx.stage.Stage stage) {
        // Primary stage unused — each workbench is its own window.
        stage.close();
        WorkbenchWindow.openNew();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
