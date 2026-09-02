package com.bloodlink.util;

import com.bloodlink.model.ReviewTag;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ReviewDialog {
    private ReviewDialog() { }

    public record ReviewInput(int rating, List<ReviewTag> tags, String comment) { }

    public static Optional<ReviewInput> show(String title, String subjectName) {
        Dialog<ReviewInput> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("How was your experience with " + subjectName + "?");

        ToggleGroup ratingGroup = new ToggleGroup();
        FlowPane starRow = new FlowPane(6, 6);
        for (int i = 1; i <= 5; i++) {
            RadioButton star = new RadioButton(i + (i == 1 ? " star" : " stars"));
            star.setToggleGroup(ratingGroup);
            star.setUserData(i);
            if (i == 5) star.setSelected(true);
            starRow.getChildren().add(star);
        }

        Map<ReviewTag, CheckBox> tagBoxes = new EnumMap<>(ReviewTag.class);
        FlowPane tagRow = new FlowPane(8, 8);
        for (ReviewTag tag : ReviewTag.values()) {
            CheckBox box = new CheckBox(tag.toString());
            tagBoxes.put(tag, box);
            tagRow.getChildren().add(box);
        }

        TextArea commentArea = new TextArea();
        commentArea.setPromptText("Optional comment (max 500 characters)");
        commentArea.setPrefRowCount(3);
        commentArea.setWrapText(true);

        Label validationLabel = new Label();
        validationLabel.getStyleClass().add("error-text");

        VBox content = new VBox(10,
                new Label("Rating"), starRow,
                new Label("What stood out?"), tagRow,
                new Label("Comment"), commentArea,
                validationLabel);
        content.setPrefWidth(420);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (ratingGroup.getSelectedToggle() == null) {
                validationLabel.setText("Choose a star rating.");
                event.consume();
            } else if (commentArea.getText() != null && commentArea.getText().length() > 500) {
                validationLabel.setText("Comment must be 500 characters or fewer.");
                event.consume();
            } else {
                validationLabel.setText("");
            }
        });

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            int rating = (int) ((RadioButton) ratingGroup.getSelectedToggle()).getUserData();
            List<ReviewTag> selectedTags = new ArrayList<>();
            for (Map.Entry<ReviewTag, CheckBox> entry : tagBoxes.entrySet())
                if (entry.getValue().isSelected()) selectedTags.add(entry.getKey());
            return new ReviewInput(rating, selectedTags, commentArea.getText());
        });
        return dialog.showAndWait();
    }
}
