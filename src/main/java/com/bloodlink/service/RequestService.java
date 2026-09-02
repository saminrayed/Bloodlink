package com.bloodlink.service;

import com.bloodlink.dao.RequestDAO;
import com.bloodlink.dao.UserDAO;
import com.bloodlink.model.*;
import com.bloodlink.util.ValidationUtil;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public final class RequestService {
    private final RequestDAO requestDAO = new RequestDAO();
    private final UserDAO userDAO = new UserDAO();
    private final MatchingService matchingService = new MatchingService();
    private final AuthorizationService authorizationService = new AuthorizationService();
    private final EligibilityService eligibilityService = new EligibilityService();

    /**
     * @param hospital   Display name of the hospital. Always required.
     * @param hospitalId Id of a curated {@link Hospital} record, or {@code null} if the
     *                    requester typed a hospital that is not in the searchable directory
     *                    yet. A null id means distance-to-hospital will show as unavailable
     *                    for this request until it is re-matched against a known hospital.
     */
    public ServiceResult<Long> create(long requesterId, BloodGroup group, int units, Urgency urgency,
                                      String hospital, Long hospitalId, String district, LocalDate deadline, String notes) {
        if (group == null || urgency == null) return ServiceResult.failure("Choose a blood group and urgency.");
        if (units < 1 || units > 20) return ServiceResult.failure("Units needed must be between 1 and 20.");
        if (ValidationUtil.isBlank(hospital) || ValidationUtil.isBlank(district))
            return ServiceResult.failure("Hospital and district are required.");
        if (deadline == null || deadline.isBefore(LocalDate.now()))
            return ServiceResult.failure("Deadline must be today or a future date.");
        try {
            authorizationService.requireSelfOrAdmin(requesterId);
            long id = requestDAO.create(requesterId, group, units, urgency, hospital, hospitalId, district, deadline, notes);
            ServiceResult<List<MatchCandidate>> matching = matchingService.match(id, requesterId);
            String message = "Request #" + id + " created. " + matching.message();
            return ServiceResult.success(message, id);
        } catch (SQLException e) {
            return ServiceResult.failure("Request could not be created: " + e.getMessage());
        }
    }

    /**
     * Eligibility (specifically the post-donation cooldown) is re-checked here, at
     * accept time, not just back when the match was created. A donor's circumstances
     * can genuinely change in between: they might have donated somewhere else and
     * entered cooldown after being notified but before responding. Matching already
     * excludes cooldown donors from ever being notified for a NEW request; this
     * closes the remaining gap where a donor could still act on an older,
     * already-delivered notification after their situation changed.
     */
    public ServiceResult<Void> accept(long requestId, long donorId) {
        try {
            authorizationService.requireSelfOrAdmin(donorId);
            Optional<User> user = userDAO.findById(donorId);
            if (user.isEmpty() || !(user.get() instanceof Donor donor)) {
                return ServiceResult.failure("Donor account not found.");
            }
            EligibilityService.EligibilityResult eligibility = eligibilityService.evaluate(donor);
            if (!eligibility.eligible()) {
                return ServiceResult.failure("You're not currently eligible to donate (" + eligibility.reason()
                        + "), so this match can no longer be accepted.");
            }
            requestDAO.acceptMatch(requestId, donorId);
            return ServiceResult.success("Request accepted.", null);
        } catch (SQLException e) {
            return ServiceResult.failure(e.getMessage());
        }
    }

    public ServiceResult<Void> decline(long requestId, long donorId) {
        return execute(() -> {
            authorizationService.requireSelfOrAdmin(donorId);
            requestDAO.declineMatch(requestId, donorId);
        }, "Match declined.");
    }

    public ServiceResult<Void> confirmDonated(long requestId, long donorId) {
        return execute(() -> {
            authorizationService.requireSelfOrAdmin(donorId);
            requestDAO.confirmDonorSide(requestId, donorId);
        }, "Confirmed. Once the requester confirms their side too, this becomes a verified donation.");
    }

    public ServiceResult<Void> confirmReceived(long requestId, long requesterId, long donorId) {
        return execute(() -> {
            authorizationService.requireSelfOrAdmin(requesterId);
            requestDAO.confirmRequesterSide(requestId, requesterId, donorId);
        }, "Confirmed. Once the donor confirms their side too, this becomes a verified donation.");
    }

    public ServiceResult<Void> cancel(long requestId, long requesterId) {
        return execute(() -> {
            authorizationService.requireSelfOrAdmin(requesterId);
            requestDAO.cancel(requestId, requesterId);
        }, "Request cancelled.");
    }

    public ServiceResult<Void> adminTransition(long requestId, long adminId, RequestStatus target, String note) {
        return execute(() -> {
            authorizationService.requireAdmin(adminId);
            requestDAO.adminTransition(requestId, adminId, target, note);
        }, "Request updated to " + target + ".");
    }

    private ServiceResult<Void> execute(SqlAction action, String successMessage) {
        try {
            action.run();
            return ServiceResult.success(successMessage, null);
        } catch (SQLException e) {
            return ServiceResult.failure(e.getMessage());
        }
    }

    @FunctionalInterface private interface SqlAction { void run() throws SQLException; }
}
