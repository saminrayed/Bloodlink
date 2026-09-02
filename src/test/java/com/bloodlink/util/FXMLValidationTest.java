package com.bloodlink.util;

import com.bloodlink.Main;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FXMLValidationTest {

    @Test
    void testAllFXMLResourcesExist() {
        List<String> fxmlFiles = List.of(
                "login.fxml",
                "register.fxml",
                "admin_dashboard.fxml",
                "donor_dashboard.fxml",
                "requester_dashboard.fxml"
        );

        for (String fxml : fxmlFiles) {
            URL resource = Main.class.getResource("/com/bloodlink/view/" + fxml);
            assertNotNull(resource, "FXML file missing: " + fxml);
        }
    }
}
