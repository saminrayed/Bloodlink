package com.bloodlink.service;

/**
 * Pure geographic-distance math. No database access, no business rules --
 * safe to call from any layer.
 */
public final class DistanceService {
    private static final double EARTH_RADIUS_KM = 6371.0;

    private DistanceService() { }

    /** Great-circle distance between two lat/lng points, in kilometers. */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
