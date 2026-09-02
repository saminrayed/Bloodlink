package com.bloodlink.service;

import com.bloodlink.dao.HospitalDAO;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves approximate distance between a donor and a request/hospital.
 * <p>
 * BloodLink is a standalone JavaFX desktop application with no access to
 * device GPS, and this project's requirements explicitly exclude live
 * GPS/maps integration. A donor's location is resolved in one of two ways,
 * in priority order:
 * <ol>
 *   <li><b>Chosen reference hospital</b> (optional): if the donor has picked
 *       the real hospital nearest to where they actually are (via the same
 *       searchable picker used for requests), that hospital's exact,
 *       curated coordinate is used directly. Two real points, real
 *       distance -- no district guesswork.</li>
 *   <li><b>Registered district</b> (default/fallback): resolved to the
 *       coordinates of that district's reference hospital (the first
 *       active hospital seeded for that district). A real coordinate, but
 *       a coarse, district-level approximation -- every caller must treat
 *       and label it as such.</li>
 * </ol>
 * "Hospital location" (the request side) is always exact: hospital
 * coordinates are curated, sourced data, not estimates.
 */
public final class LocationService {
    private final HospitalDAO hospitalDAO = new HospitalDAO();
    private final Map<String, Optional<double[]>> districtCache = new ConcurrentHashMap<>();
    private final Map<Long, Optional<double[]>> referenceHospitalCache = new ConcurrentHashMap<>();

    /**
     * The reference coordinate for a district, cached for the lifetime of
     * this LocationService instance. Callers that resolve distance for many
     * rows in one pass (e.g. ranking hundreds of donors) should reuse a
     * single LocationService instance for that pass instead of creating a
     * new one per row, so this cache actually avoids repeated lookups.
     */
    public Optional<double[]> districtReferencePoint(String district) {
        if (district == null || district.isBlank()) return Optional.empty();
        String key = district.trim().toLowerCase();
        return districtCache.computeIfAbsent(key, ignored -> {
            try {
                return hospitalDAO.findDistrictReferencePoint(district);
            } catch (SQLException e) {
                return Optional.empty();
            }
        });
    }

    private Optional<double[]> referenceHospitalPoint(long hospitalId) {
        return referenceHospitalCache.computeIfAbsent(hospitalId, id -> {
            try {
                return hospitalDAO.findById(id).map(h -> new double[]{h.latitude(), h.longitude()});
            } catch (SQLException e) {
                return Optional.empty();
            }
        });
    }

    /**
     * A donor's resolved location point: their chosen reference hospital if set and
     * still valid, otherwise their district's reference point.
     */
    public Optional<double[]> donorLocation(String donorDistrict, Long donorReferenceHospitalId) {
        if (donorReferenceHospitalId != null) {
            Optional<double[]> chosen = referenceHospitalPoint(donorReferenceHospitalId);
            if (chosen.isPresent()) return chosen;
            // Hospital was deactivated or removed since the donor picked it -- fall through
            // to the district default rather than failing distance resolution entirely.
        }
        return districtReferencePoint(donorDistrict);
    }

    /**
     * Approximate distance in kilometers from a donor to a specific hospital
     * coordinate, using the donor's chosen reference hospital when set, otherwise
     * their district. Empty when either side is unknown -- callers must show
     * "distance unavailable" rather than a fabricated number in that case.
     */
    public Optional<Double> distanceKm(String donorDistrict, Long donorReferenceHospitalId, Double hospitalLatitude, Double hospitalLongitude) {
        if (hospitalLatitude == null || hospitalLongitude == null) return Optional.empty();
        Optional<double[]> donorPoint = donorLocation(donorDistrict, donorReferenceHospitalId);
        if (donorPoint.isEmpty()) return Optional.empty();
        double[] point = donorPoint.get();
        double km = DistanceService.haversineKm(point[0], point[1], hospitalLatitude, hospitalLongitude);
        return Optional.of(Math.round(km * 10.0) / 10.0);
    }

    /** Convenience overload for callers that don't have (or don't yet use) a reference hospital. */
    public Optional<Double> distanceKm(String donorDistrict, Double hospitalLatitude, Double hospitalLongitude) {
        return distanceKm(donorDistrict, null, hospitalLatitude, hospitalLongitude);
    }
}
