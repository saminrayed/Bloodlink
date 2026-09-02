package com.bloodlink.util;

import com.bloodlink.model.NidExtraction;
import com.bloodlink.service.OcrService;
import com.bloodlink.service.TesseractOcrService;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.time.LocalDate;
import java.util.Optional;

/**
 * The "upload NID -&gt; OCR -&gt; review/edit -&gt; confirm" workflow the spec
 * requires: OCR output is never silently trusted, always shown to the user
 * for correction before it touches any real form field. The photographed
 * file is read once (by {@link OcrService}) and never copied, stored, or
 * logged anywhere by this class or its caller.
 * <p>
 * The {@link OcrService} used here is swappable -- change {@link #ocrService}
 * to a different implementation without touching any caller of {@link #show}.
 */
public final class NidScanDialog {
    private static final OcrService ocrService = new TesseractOcrService();

    private NidScanDialog() { }

    public record NidReviewResult(String name, LocalDate birthDate) { }

    /** Returns empty if the user cancels the file picker or the review dialog -- never partial/unconfirmed data. */
    public static Optional<NidReviewResult> show(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select a photo of your NID card");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"));
        File file = chooser.showOpenDialog(owner);
        if (file == null) return Optional.empty();

        NidExtraction extraction = ocrService.extract(file);
        return reviewDialog(extraction).showAndWait();
    }

    private static Dialog<NidReviewResult> reviewDialog(NidExtraction extraction) {
        Dialog<NidReviewResult> dialog = new Dialog<>();
        dialog.setTitle("Review detected information");
        dialog.setHeaderText(extraction.success()
                ? "Check the information below before using it -- OCR can make mistakes."
                : "Automatic detection didn't work this time.");

        TextField nameField = new TextField(extraction.detectedName() == null ? "" : extraction.detectedName());
        nameField.setPromptText("Full name");
        DatePicker dobPicker = new DatePicker(extraction.detectedBirthDate());

        Label nidLabel = new Label(extraction.detectedNidNumberMasked() == null
                ? "NID number: not detected"
                : "NID number on card: " + extraction.detectedNidNumberMasked() + " (shown for your reference only -- never stored)");
        nidLabel.setWrapText(true);

        Label statusLabel = new Label(extraction.success() ? "" : extraction.failureReason());
        statusLabel.setWrapText(true);
        statusLabel.getStyleClass().add(extraction.success() ? "helper-text" : "error-text");

        VBox content = new VBox(10,
                new Label("This is identity-registration assistance only -- not medical or eligibility verification."),
                new Label("Detected name (edit if wrong)"), nameField,
                new Label("Detected date of birth (edit if wrong)"), dobPicker,
                nidLabel, statusLabel);
        content.setPrefWidth(400);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).setText("Use This Information");

        dialog.setResultConverter(button ->
                button == ButtonType.OK ? new NidReviewResult(nameField.getText(), dobPicker.getValue()) : null);
        return dialog;
    }
}
