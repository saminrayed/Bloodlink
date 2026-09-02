package com.bloodlink.model;

import java.time.LocalDate;

/**
 * The result of one OCR pass over a photographed NID card. Identity-
 * registration assistance ONLY -- never medical verification, never proof
 * of blood group, eligibility, or fitness to donate. This is a form-fill
 * shortcut, not an identity-verification record: nothing here is persisted
 * to the database. {@code detectedNidNumberMasked} exists only so the
 * on-screen review dialog can show the user "yes, this looks like your
 * card" -- it is never stored, logged, or passed anywhere beyond that one
 * dialog.
 */
public record NidExtraction(boolean success, String detectedName, LocalDate detectedBirthDate,
                            String detectedNidNumberMasked, String failureReason) {
    public static NidExtraction failure(String reason) {
        return new NidExtraction(false, null, null, null, reason);
    }
}
