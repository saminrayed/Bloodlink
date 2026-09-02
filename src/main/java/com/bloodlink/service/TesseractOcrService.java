package com.bloodlink.service;

import com.bloodlink.model.NidExtraction;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs a real, local OCR pass (Tesseract, via the Tess4J JNA binding) over a
 * photographed Bangladesh NID card and heuristically pulls out name /
 * date-of-birth / NID number from the recognized text. Identity-registration
 * ASSISTANCE only -- see {@link com.bloodlink.model.NidExtraction}'s Javadoc
 * for the boundary this must never cross.
 * <p>
 * <b>Real operational requirement, not just a Maven dependency</b>: this needs
 * a local Tesseract OCR engine actually installed on the machine (e.g. the
 * UB-Mannheim build on Windows) with English trained data available, either
 * on the system PATH or pointed to via the {@code TESSDATA_PREFIX}
 * environment variable -- the same variable Tesseract itself conventionally
 * uses, so no BloodLink-specific configuration is invented here. If
 * Tesseract isn't installed, isn't found, or fails to read a given image,
 * every method here returns a graceful {@link NidExtraction#failure} rather
 * than throwing -- registration must keep working manually regardless.
 * <p>
 * <b>Honesty note carried over from this delivery's other native-dependent
 * piece (the push server)</b>: I could not compile or run this against a
 * real Tesseract installation in this environment to mechanically verify it
 * the way the rest of this delivery was cross-checked line by line. The
 * Tess4J API surface used here (Tesseract, TesseractException, doOCR(File),
 * setDatapath, setLanguage) has been stable across many releases, but this
 * file carries a different, weaker guarantee than everything else delivered
 * this session. Test it against a real card photo before relying on it.
 */
public final class TesseractOcrService implements OcrService {
    private static final Pattern NAME_PATTERN = Pattern.compile("(?i)name\\s*[:\\-]?\\s*([A-Za-z .'\\-]{3,60})");
    private static final Pattern DOB_PATTERN = Pattern.compile(
            "(?i)date\\s*of\\s*birth\\s*[:\\-]?\\s*(\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{4})");
    private static final Pattern NID_PATTERN = Pattern.compile("(?i)(?:national\\s*id\\s*no|id\\s*no|nid\\s*no)\\s*[:\\-]?\\s*([0-9]{10,17})");
    private static final DateTimeFormatter[] DOB_FORMATS = {
            DateTimeFormatter.ofPattern("d MMM yyyy"), DateTimeFormatter.ofPattern("d MMMM yyyy")
    };

    @Override
    public NidExtraction extract(File imageFile) {
        if (imageFile == null || !imageFile.exists()) return NidExtraction.failure("No image file was provided.");
        try {
            Tesseract tesseract = new Tesseract();
            String tessdataPath = System.getenv("TESSDATA_PREFIX");
            if (tessdataPath != null && !tessdataPath.isBlank()) tesseract.setDatapath(tessdataPath);
            tesseract.setLanguage("eng");
            String rawText = tesseract.doOCR(imageFile);
            return parse(rawText);
        } catch (TesseractException e) {
            return NidExtraction.failure("The OCR engine could not read this image. Try a clearer, well-lit, unrotated photo.");
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            // Expected on a machine without Tesseract installed -- not a bug, not logged as one.
            return NidExtraction.failure("Local OCR (Tesseract) is not installed on this machine. You can fill in your details manually below.");
        }
    }

    private NidExtraction parse(String rawText) {
        if (rawText == null || rawText.isBlank())
            return NidExtraction.failure("No readable text was found in the image.");
        String name = firstGroup(NAME_PATTERN, rawText);
        LocalDate dob = parseDate(firstGroup(DOB_PATTERN, rawText));
        String nid = firstGroup(NID_PATTERN, rawText);
        if (name == null && dob == null && nid == null) {
            return NidExtraction.failure("Could not confidently detect any fields on this card. " +
                    "Try a clearer photo, or fill in your details manually.");
        }
        return new NidExtraction(true, name, dob, nid == null ? null : maskNid(nid), null);
    }

    private String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null) return null;
        for (DateTimeFormatter format : DOB_FORMATS) {
            try { return LocalDate.parse(raw, format); } catch (RuntimeException ignored) { /* try next format */ }
        }
        return null;
    }

    /** Only the last 4 digits are ever surfaced -- the full number is never returned, stored, or logged. */
    private String maskNid(String nid) {
        return nid.length() <= 4 ? "••••" : "•".repeat(nid.length() - 4) + nid.substring(nid.length() - 4);
    }
}
