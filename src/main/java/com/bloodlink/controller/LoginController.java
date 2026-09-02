package com.bloodlink.controller;

import com.bloodlink.model.Role;
import com.bloodlink.service.AuthService;
import com.bloodlink.util.BackgroundTasks;
import com.bloodlink.util.SceneManager;
import com.bloodlink.util.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public final class LoginController {
    @FXML private ComboBox<Role> roleCombo;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button signInButton;

    private final AuthService authService = new AuthService();

    @FXML private void initialize() {
        roleCombo.getItems().setAll(Role.DONOR, Role.REQUESTER, Role.ADMIN);
        roleCombo.setValue(Role.DONOR);
        errorLabel.setText("");
        passwordField.setOnAction(event -> signIn());
    }

    /**
     * Credential checking is a database call, so this now runs off the JavaFX
     * Application Thread like every other DB-triggered action in the app --
     * the same fix applied to dashboard polling, just never carried back to
     * this screen until now.
     */
    @FXML private void signIn() {
        errorLabel.setText("");
        signInButton.setDisable(true);
        String email = emailField.getText();
        String password = passwordField.getText();
        Role role = roleCombo.getValue();
        BackgroundTasks.run(() -> authService.login(email, password, role),
                result -> {
                    signInButton.setDisable(false);
                    if (!result.success()) { errorLabel.setText(result.message()); return; }
                    SessionManager.getInstance().setCurrentUser(result.data());
                    SceneManager.showDashboard(result.data().getRole());
                },
                error -> {
                    signInButton.setDisable(false);
                    errorLabel.setText("Sign in failed: " + (error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName()));
                });
    }

    @FXML private void openRegistration() { SceneManager.showRegister(); }
}
