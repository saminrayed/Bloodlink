package com.bloodlink.service;

import com.bloodlink.model.NidExtraction;

import java.io.File;

/**
 * The integration boundary for NID-assisted registration. Every caller talks
 * to this interface, never to a specific OCR engine directly, so the
 * underlying implementation can be swapped (a different local engine, a
 * different parsing strategy) without touching {@code RegisterController} or
 * any other caller. See {@link TesseractOcrService} for the current
 * implementation and its real operational requirements.
 */
public interface OcrService {
    /** Never throws for a missing/broken OCR engine -- returns a failure result so manual entry always remains available. */
    NidExtraction extract(File imageFile);
}
