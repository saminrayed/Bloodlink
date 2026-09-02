package com.bloodlink.util;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.Optional;

public final class PasswordDialog {
    private PasswordDialog() { }

    public static Optional<String> show(String title, String header) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Temporary password");
        PasswordField confirmationField = new PasswordField();
        confirmationField.setPromptText("Confirm temporary password");
        Label validationLabel = new Label();
        validationLabel.getStyleClass().add("error-text");

        VBox content = new VBox(10,
                new Label("Temporary password"), passwordField,
                new Label("Confirm password"), confirmationField,
                validationLabel
        );
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (!passwordField.getText().equals(confirmationField.getText())) {
                validationLabel.setText("Passwords do not match.");
                event.consume();
            } else {
                validationLabel.setText("");
            }
        });
        dialog.setResultConverter(button -> button == ButtonType.OK ? passwordField.getText() : null);
        return dialog.showAndWait();
    }
}
