package com.bloodlink.service;

import com.bloodlink.dao.DonorDAO;
import com.bloodlink.dao.RequestDAO;
import com.bloodlink.dao.ReviewDAO;
import com.bloodlink.model.*;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class MatchingService {
    private final DonorDAO donorDAO = new DonorDAO();
    private final RequestDAO requestDAO = new RequestDAO();
    private final ReviewDAO reviewDAO = new ReviewDAO();
    private final EligibilityService eligibilityService = new EligibilityService();
    private final AuthorizationService authorizationService = new AuthorizationService();

    /**
     * How far a donor may be before an urgency level stops considering them at all --
     * not just a scoring nudge, an actual inclusion cutoff, per the notification
     * engine's "don't blast a routine request to the whole country" requirement.
     * Only applied when distance is actually known: an unmatched hospital or an
     * uncovered district must never be treated as "too far", since that would
     * silently stop matching donors entirely in the districts the hospital
     * directory doesn't cover yet. Simple named constants rather than a config
     * subsystem, consistent with the distance/reputation scoring bands below --
     * this app doesn't otherwise have a tunable-business-rules config layer, and
     * three numbers don't justify building one.
     */
    private static final double NORMAL_RADIUS_KM = 15.0;
    private static final double URGENT_RADIUS_KM = 40.0;
    private static final double CRITICAL_RADIUS_KM = 100.0;

    public ServiceResult<List<MatchCandidate>> match(long requestId, long requesterId) {
        try {
            authorizationService.requireSelfOrAdmin(requesterId);
            BloodRequest request = requestDAO.findById(requestId)
                    .orElseThrow(() -> new SQLException("Request not found."));
            if (request.requesterId() != requesterId) {
                throw new SQLException("You do not own this request.");
            }
            // One LocationService per matching pass so its district-lookup cache is
            // reused across every donor instead of re-querying the same district
            // repeatedly -- important once the donor pool reaches hundreds/thousands.
            LocationService locationService = new LocationService();
            List<Donor> availableDonors = donorDAO.findAvailableDonors();
            // Reputations for the whole pool in one query, not one query per donor.
            Map<Long, ReputationSummary> reputations = reviewDAO.reputationsOf(availableDonors.stream().map(Donor::getId).toList());
            // Donors already notified/responded for THIS request are never reconsidered --
            // rematching only ever adds genuinely new candidates. Computed here (not just
            // left to RequestDAO.saveMatches) so the count/message returned to the
            // requester is accurate, not "8 matched" when 6 of those were already notified.
            java.util.Set<Long> alreadyMatchedDonorIds = requestDAO.findMatchedDonorIds(requestId);
            double radiusKm = radiusKmFor(request.urgency());
            List<MatchCandidate> candidates = new ArrayList<>();
            for (Donor donor : availableDonors) {
                if (alreadyMatchedDonorIds.contains(donor.getId())) continue;
                EligibilityService.EligibilityResult eligibility = eligibilityService.evaluate(donor);
                if (!eligibility.eligible() || !donor.getBloodGroup().canDonateTo(request.bloodGroup())) continue;
                Double distanceKm = locationService.distanceKm(donor.getDistrict(), donor.getReferenceHospitalId(),
                        request.hospitalLatitude(), request.hospitalLongitude()).orElse(null);
                if (distanceKm != null && distanceKm > radiusKm) continue;
                ReputationSummary reputation = reputations.getOrDefault(donor.getId(), ReputationSummary.none(donor.getId()));
                double score = calculateScore(request, donor, distanceKm, reputation);
                String reason = buildReason(request, donor, distanceKm, reputation);
                candidates.add(new MatchCandidate(donor.getId(), donor.getFullName(), donor.getBloodGroup(),
                        donor.getDistrict(), donor.getPhone(), score, reason, donor.getAvailabilityStatus(),
                        donor.getBadgeTier(), distanceKm,
                        reputation.hasReviews() ? reputation.averageRating() : null, reputation.reviewCount(),
                        MatchStatus.NOTIFIED, false, false));
            }
            candidates.sort(Comparator.comparingDouble(MatchCandidate::score).reversed()
                    .thenComparing(MatchCandidate::donorName));
            // Notify a wider pool than strictly unitsNeeded, since not everyone notified
            // will accept -- rule of thumb 3x the remaining need, floored at 8 so a
            // single-unit request still gets a reasonable spread of candidates.
            int poolSize = Math.max(8, request.unitsRemaining() * 3);
            List<MatchCandidate> topCandidates = candidates.stream().limit(poolSize).toList();
            requestDAO.saveMatches(requestId, requesterId, topCandidates);
            return ServiceResult.success(topCandidates.isEmpty() ? "No eligible donor is available yet."
                    : topCandidates.size() + " eligible donor(s) matched.", topCandidates);
        } catch (SQLException e) {
            return ServiceResult.failure("Matching failed: " + e.getMessage());
        }
    }

    private double radiusKmFor(Urgency urgency) {
        return switch (urgency) {
            case NORMAL -> NORMAL_RADIUS_KM;
            case URGENT -> URGENT_RADIUS_KM;
            case CRITICAL -> CRITICAL_RADIUS_KM;
        };
    }

    private double calculateScore(BloodRequest request, Donor donor, Double distanceKm, ReputationSummary reputation) {
        double score = 30;
        if (donor.getBloodGroup() == request.bloodGroup()) score += 20;
        if (donor.getDistrict().equalsIgnoreCase(request.district())) score += 35;
        if (donor.getLastDonationDate() == null) score += 10;
        else score += Math.min(10, ChronoUnit.DAYS.between(donor.getLastDonationDate(), LocalDate.now()) / 30.0);
        score += Math.min(5, donor.getVerifiedDonationCount() * 0.5);
        score += request.urgency().getWeight();
        score += distanceScore(distanceKm);
        score += reputationScore(reputation);
        return Math.round(score * 10.0) / 10.0;
    }

    /**
     * Distance-based bonus on top of the coarser same-district bonus above.
     * Zero (never negative) when distance is unknown -- an unmatched hospital
     * must not penalize a donor who might otherwise be the best candidate.
     */
    private double distanceScore(Double distanceKm) {
        if (distanceKm == null) return 0;
        if (distanceKm <= 5) return 15;
        if (distanceKm <= 15) return 10;
        if (distanceKm <= 30) return 5;
        return 0;
    }

    /**
     * Reputation is a ranking factor among already-compatible donors, never a filter --
     * a donor with zero reviews (everyone's starting point) gets a neutral 0, not a
     * penalty. A donor rated below 3 stars on average gets no bonus either; only
     * ratings above 3 add points, scaled up to +10 at a perfect 5.0.
     */
    private double reputationScore(ReputationSummary reputation) {
        if (!reputation.hasReviews()) return 0;
        return Math.max(0, (reputation.averageRating() - 3.0) * 5.0);
    }

    private String buildReason(BloodRequest request, Donor donor, Double distanceKm, ReputationSummary reputation) {
        List<String> reasons = new ArrayList<>();
        reasons.add(donor.getBloodGroup() == request.bloodGroup() ? "exact blood group" : "compatible blood group");
        reasons.add(donor.getDistrict().equalsIgnoreCase(request.district()) ? "same district" : "different district");
        reasons.add(donor.getLastDonationDate() == null ? "no recent donation" : "cooldown complete");
        if (donor.getVerifiedDonationCount() > 0) reasons.add(donor.getVerifiedDonationCount() + " verified donation(s)");
        reasons.add(distanceKm != null ? "~" + distanceKm + " km from hospital" : "distance unavailable");
        if (reputation.hasReviews()) reasons.add(reputation.averageRating() + "\u2605 (" + reputation.reviewCount() + " review(s))");
        return String.join(", ", reasons);
    }
}
