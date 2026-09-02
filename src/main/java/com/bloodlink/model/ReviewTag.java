package com.bloodlink.model;

/** Fixed, small tag set -- deliberately not a free-text or many-to-many table for five options. */
public enum ReviewTag {
    RELIABLE("Reliable"), PUNCTUAL("Punctual"), RESPONSIVE("Responsive"),
    HELPFUL("Helpful"), COOPERATIVE("Cooperative");

    private final String label;

    ReviewTag(String label) { this.label = label; }

    @Override
    public String toString() { return label; }
}
