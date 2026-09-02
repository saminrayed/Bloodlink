package com.bloodlink.model;

/**
 * A curated hospital record with real coordinates, used for the searchable
 * hospital picker on request creation and for distance-aware matching.
 * Coordinates are sourced (not fabricated) for every seeded row -- see
 * migration_002_hospitals_and_distance.sql.
 */
public record Hospital(long id, String name, String district, String area, String address,
                        double latitude, double longitude, String phone, boolean active) {

    /** Display label used by the searchable ComboBox and match-reason text. */
    @Override
    public String toString() {
        return area == null || area.isBlank() ? name + " (" + district + ")" : name + " — " + area + " (" + district + ")";
    }
}
