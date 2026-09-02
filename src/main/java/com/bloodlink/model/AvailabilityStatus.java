package com.bloodlink.model;

public enum AvailabilityStatus {
    AVAILABLE("Available"), BUSY("Busy"), OUT_OF_TOWN("Out of Town"), MEDICAL_HOLD("Medical Hold");

    private final String label;
    AvailabilityStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
    @Override public String toString() { return label; }
}
