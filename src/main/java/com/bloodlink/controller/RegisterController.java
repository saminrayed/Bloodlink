package com.bloodlink.controller;

import com.bloodlink.model.*;
import com.bloodlink.service.AuthService;
import com.bloodlink.util.AlertUtil;
import com.bloodlink.util.BackgroundTasks;
import com.bloodlink.util.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public final class RegisterController {
    @FXML private ComboBox<Role> roleCombo;
    @FXML private TextField fullNameField;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private TextField districtField;
    @FXML private TextArea addressArea;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private VBox donorFields;
    @FXML private ComboBox<BloodGroup> bloodGroupCombo;
    @FXML private DatePicker birthDatePicker;
    @FXML private TextField weightField;
    @FXML private DatePicker lastDonationPicker;
    @FXML private Label errorLabel;
    @FXML private Button createButton;
    @FXML private Button scanNidButton;

    private final AuthService authService = new AuthService();

    @FXML private void initialize() {
        roleCombo.getItems().setAll(Role.DONOR, Role.REQUESTER);
        roleCombo.setValue(Role.DONOR);
        bloodGroupCombo.getItems().setAll(BloodGroup.values());
        roleCombo.valueProperty().addListener((obs, oldValue, newValue) -> updateDonorFields());
        updateDonorFields();
    }

    private void updateDonorFields() {
        boolean donor = roleCombo.getValue() == Role.DONOR;
        donorFields.setVisible(donor);
        donorFields.setManaged(donor);
    }

    /**
     * Registration is a database write, so this now runs off the JavaFX
     * Application Thread like every other DB-triggered action in the app --
     * the same fix applied to dashboard polling, just never carried back to
     * this screen until now.
     */
    @FXML private void createAccount() {
        Double weight = null;
        if (roleCombo.getValue() == Role.DONOR && !weightField.getText().isBlank()) {
            try { weight = Double.parseDouble(weightField.getText().trim()); }
            catch (NumberFormatException e) { errorLabel.setText("Weight must be numeric."); return; }
        }
        RegistrationData data = new RegistrationData(roleCombo.getValue(), fullNameField.getText(), emailField.getText(),
                phoneField.getText(), districtField.getText(), addressArea.getText(), passwordField.getText(),
                bloodGroupCombo.getValue(), birthDatePicker.getValue(), weight, lastDonationPicker.getValue());
        String confirmPassword = confirmPasswordField.getText();
        errorLabel.setText("");
        createButton.setDisable(true);
        BackgroundTasks.run(() -> authService.register(data, confirmPassword),
                result -> {
                    createButton.setDisable(false);
                    if (!result.success()) { errorLabel.setText(result.message()); return; }
                    AlertUtil.info("Account created", result.message());
                    SceneManager.showLogin();
                },
                error -> {
                    createButton.setDisable(false);
                    errorLabel.setText("Registration failed: " + (error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName()));
                });
    }

    @FXML private void backToLogin() { SceneManager.showLogin(); }

    /**
     * Optional identity-registration assist, per the spec's required
     * workflow: upload -> OCR -> user reviews/edits -> user confirms -> only
     * then does anything touch a real form field. Never pre-fills blood
     * group, never sets any "verified" flag -- this is a form-fill shortcut
     * for name and date of birth, nothing more.
     */
    @FXML private void scanNid() {
        NidScanDialog.show(scanNidButton.getScene().getWindow()).ifPresent(result -> {
            if (result.name() != null && !result.name().isBlank()) fullNameField.setText(result.name());
            if (result.birthDate() != null) birthDatePicker.setValue(result.birthDate());
        });
    }
}
