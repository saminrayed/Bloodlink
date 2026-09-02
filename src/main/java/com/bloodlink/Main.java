package com.bloodlink;

import com.bloodlink.util.AlertUtil;
import com.bloodlink.util.DatabaseSetup;
import com.bloodlink.util.DBConnection;
import com.bloodlink.util.SceneManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public final class Main extends Application {
    @Override public void start(Stage stage) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            throwable.printStackTrace();
            Platform.runLater(() -> AlertUtil.error("Unexpected error", "An unexpected error occurred. See the application log for details."));
        });
        SceneManager.initialize(stage);
        DatabaseSetup.ensureInitialized();
        SceneManager.showLogin();
        if (!DBConnection.testConnection()) {
            Platform.runLater(() -> AlertUtil.warning("Database connection unavailable",
                    "BloodLink opened, but database connection could not be established. Please check your database settings."));
        }
    }

    public static void main(String[] args) { launch(args); }
}
