package com.bloodlink.service;

import com.bloodlink.dao.AuditDAO;
import com.bloodlink.dao.RequestDAO;
import com.bloodlink.dao.ReviewDAO;
import com.bloodlink.model.*;
import com.bloodlink.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * A review may only be submitted by the donor or requester of a request that
 * has reached {@link RequestStatus#FULFILLED} (both sides confirmed the
 * donation happened via {@link RequestService#confirmDonated} /
 * {@link RequestService#confirmReceived}), about the other party in
 * that same request, and only once per request per reviewer. All of this is
 * enforced here rather than left to the UI to prevent, since a review is a
 * reputation claim about another person and must be backed by a real,
 * verified interaction.
 */
public final class ReviewService {
    private final ReviewDAO reviewDAO = new ReviewDAO();
    private final RequestDAO requestDAO = new RequestDAO();
    private final AuthorizationService authorizationService = new AuthorizationService();

    public ServiceResult<Void> submit(long requestId, long reviewerId, int rating, List<ReviewTag> tags, String comment) {
        if (rating < 1 || rating > 5) return ServiceResult.failure("Rating must be between 1 and 5.");
        try {
            authorizationService.requireSelfOrAdmin(reviewerId);
            BloodRequest request = requestDAO.findById(requestId).orElse(null);
            if (request == null) return ServiceResult.failure("Request not found.");
            if (request.status() != RequestStatus.FULFILLED)
                return ServiceResult.failure("This request is not a verified completed donation yet.");
            Long reviewedId = resolveReviewedParty(request, reviewerId);
            if (reviewedId == null) return ServiceResult.failure("You were not a party to this request.");
            if (reviewDAO.hasReviewed(requestId, reviewerId))
                return ServiceResult.failure("You already reviewed this donation.");

            try (Connection connection = DBConnection.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    reviewDAO.create(connection, requestId, reviewerId, reviewedId, rating, tags, comment);
                    new AuditDAO().log(connection, reviewerId, "SUBMIT_REVIEW", "USER", reviewedId,
                            "Rated " + rating + "/5 for request #" + requestId);
                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
            return ServiceResult.success("Review submitted. Thank you for keeping BloodLink trustworthy.", null);
        } catch (SQLException e) {
            return ServiceResult.failure(e.getMessage());
        }
    }

    private Long resolveReviewedParty(BloodRequest request, long reviewerId) {
        if (request.requesterId() == reviewerId) return request.acceptedDonorId();
        if (request.acceptedDonorId() != null && request.acceptedDonorId() == reviewerId) return request.requesterId();
        return null;
    }

    public boolean hasReviewed(long requestId, long reviewerId) {
        try {
            return reviewDAO.hasReviewed(requestId, reviewerId);
        } catch (SQLException e) {
            return false;
        }
    }

    public ReputationSummary reputationOf(long userId) {
        try {
            return reviewDAO.reputationOf(userId);
        } catch (SQLException e) {
            return ReputationSummary.none(userId);
        }
    }

    public List<Review> reviewsReceivedBy(long userId, int limit) throws SQLException {
        return reviewDAO.findReceivedBy(userId, limit);
    }
}
