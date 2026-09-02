package com.bloodlink.util;

import com.bloodlink.Main;
import com.bloodlink.model.Role;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public final class SceneManager {
    private static Stage stage;
    private SceneManager() { }

    public static void initialize(Stage primaryStage) { stage = primaryStage; }

    public static void showLogin() { setScene("login.fxml", "BloodLink — Sign in", 1060, 720); }
    public static void showRegister() { setScene("register.fxml", "BloodLink — Create account", 1120, 760); }

    public static void showDashboard(Role role) {
        switch (role) {
            case DONOR -> setScene("donor_dashboard.fxml", "BloodLink — Donor", 1380, 840);
            case REQUESTER -> setScene("requester_dashboard.fxml", "BloodLink — Requester", 1380, 840);
            case ADMIN -> setScene("admin_dashboard.fxml", "BloodLink — Admin", 1460, 900);
        }
    }

    public static void logout() {
        SessionManager.getInstance().clear();
        showLogin();
    }

    private static void setScene(String fxml, String title, double width, double height) {
        URL resource = Main.class.getResource("/com/bloodlink/view/" + fxml);
        if (resource == null) throw new IllegalStateException("Missing FXML resource: " + fxml);
        try {
            Parent root = FXMLLoader.load(resource);
            Scene scene = new Scene(root, width, height);
            stage.setScene(scene);
            stage.setTitle(title);
            stage.setMinWidth(Math.min(width, 1000));
            stage.setMinHeight(Math.min(height, 680));
            stage.centerOnScreen();
            stage.show();
        } catch (IOException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException("Unable to load screen: " + fxml + " (" + (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName()) + ")", cause);
        }
    }
}
