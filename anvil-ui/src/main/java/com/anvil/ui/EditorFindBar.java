package com.anvil.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.input.KeyCode;

/** In-editor text search (Ctrl+F). */
final class EditorFindBar extends HBox {

    private final TextField query = new TextField();
    private final Label status = new Label("");
    private Runnable onFind;

    EditorFindBar() {
        super(8);
        getStyleClass().add("find-bar");
        setAlignment(Pos.CENTER_LEFT);
        setVisible(false);
        setManaged(false);

        query.setPromptText("Find in file…");
        query.setPrefWidth(200);
        HBox.setHgrow(query, Priority.ALWAYS);
        query.textProperty().addListener((obs, o, n) -> runFind());
        query.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                hide();
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                runFind();
                e.consume();
            }
        });

        status.getStyleClass().add("find-status");
        getChildren().addAll(query, status);
    }

    void bind(Runnable findAction) {
        this.onFind = findAction;
    }

    void show() {
        setVisible(true);
        setManaged(true);
        query.requestFocus();
        query.selectAll();
    }

    void hide() {
        setVisible(false);
        setManaged(false);
        status.setText("");
    }

    String queryText() {
        return query.getText();
    }

    void setStatus(String text) {
        status.setText(text);
    }

    private void runFind() {
        if (onFind != null) {
            onFind.run();
        }
    }
}
