package com.bloodlink.dao;

import com.bloodlink.model.*;
import com.bloodlink.service.LocationService;
import com.bloodlink.util.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Multi-donor model: a request needing N units is satisfied by up to N
 * different donors, each independently accepting and going through their
 * own two-sided handshake (tracked per-row on request_matches, not on
 * blood_requests). blood_requests.status is derived from real state
 * (units_fulfilled, and how many matches are in each status) via
 * {@link #recomputeStatus}, rather than hand-tracked through every
 * transition -- see that method for the exact derivation rules.
 * <p>
 * blood_requests.accepted_donor_id/donor_confirmed_at/requester_confirmed_at
 * are legacy columns from the single-donor model, kept only for
 * backward-compatible reads of pre-migration data. Nothing in this class
 * writes to them anymore.
 */
public final class RequestDAO {
    public long create(long requesterId, BloodGroup bloodGroup, int units, Urgency urgency,
                       String hospital, Long hospitalId, String district, LocalDate deadline, String notes) throws SQLException {
        String sql = """
                INSERT INTO blood_requests(requester_id,blood_group,units_needed,urgency,hospital_name,hospital_id,district,deadline,notes,status)
                VALUES(?,?,?,?,?,?,?,?,?, 'PENDING')
                """;
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, requesterId);
                statement.setString(2, bloodGroup.name());
                statement.setInt(3, units);
                statement.setString(4, urgency.name());
                statement.setString(5, hospital.trim());
                if (hospitalId == null) statement.setNull(6, Types.BIGINT); else statement.setLong(6, hospitalId);
                statement.setString(7, district.trim());
                statement.setObject(8, deadline);
                statement.setString(9, notes == null ? "" : notes.trim());
                statement.executeUpdate();
                long id;
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Request ID was not generated.");
                    id = keys.getLong(1);
                }
                insertHistory(connection, id, null, RequestStatus.PENDING, requesterId, "Emergency request submitted");
                new AuditDAO().log(connection, requesterId, "CREATE_REQUEST", "BLOOD_REQUEST", id, bloodGroup + ", " + units + " unit(s)");
                connection.commit();
                return id;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public Optional<BloodRequest> findById(long requestId) throws SQLException {
        String sql = baseSelect() + " WHERE br.id=?";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            try (ResultSet rs = statement.executeQuery()) { return rs.next() ? Optional.of(mapRequest(rs)) : Optional.empty(); }
        }
    }

    public List<BloodRequest> findByRequester(long requesterId) throws SQLException {
        String sql = baseSelect() + " WHERE br.requester_id=? ORDER BY br.created_at DESC";
        List<BloodRequest> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requesterId);
            try (ResultSet rs = statement.executeQuery()) { while (rs.next()) rows.add(mapRequest(rs)); }
        }
        return rows;
    }

    public List<RequestStatusHistoryEntry> findStatusHistory(long requestId, long requesterId) throws SQLException {
        String sql = """
                SELECT h.id,h.request_id,h.from_status,h.to_status,u.full_name changed_by_name,h.note,h.changed_at
                FROM request_status_history h
                JOIN blood_requests br ON br.id=h.request_id
                LEFT JOIN users u ON u.id=h.changed_by
                WHERE h.request_id=? AND br.requester_id=?
                ORDER BY h.changed_at DESC,h.id DESC
                """;
        List<RequestStatusHistoryEntry> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            statement.setLong(2, requesterId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String from = rs.getString("from_status");
                    rows.add(new RequestStatusHistoryEntry(rs.getLong("id"), rs.getLong("request_id"),
                            from == null ? null : RequestStatus.valueOf(from),
                            RequestStatus.valueOf(rs.getString("to_status")),
                            rs.getString("changed_by_name"), rs.getString("note"),
                            rs.getTimestamp("changed_at").toLocalDateTime()));
                }
            }
        }
        return rows;
    }

    private record DonorMatchRow(long donorId, String name, BloodGroup group, String district, String phone, double score,
                                 String reason, AvailabilityStatus availability, int verifiedCount, Long referenceHospitalId,
                                 Double hospitalLat, Double hospitalLon, MatchStatus matchStatus,
                                 boolean donorConfirmed, boolean requesterConfirmed) { }

    /**
     * Every donor match for one request -- NOTIFIED, ACCEPTED, DECLINED, and EXPIRED
     * alike, so the requester can see the full picture and act on any currently-ACCEPTED
     * row. Distance and reputation are batch-resolved, not per row.
     */
    public List<MatchCandidate> findMatchesForRequest(long requestId) throws SQLException {
        String sql = """
                SELECT rm.donor_id,u.full_name,d.blood_group,u.district,u.phone,rm.match_score,rm.match_reason,
                       d.availability_status,d.verified_donation_count,d.reference_hospital_id,
                       rm.status AS match_status, rm.donor_confirmed_at, rm.requester_confirmed_at,
                       h.latitude AS hospital_latitude, h.longitude AS hospital_longitude
                FROM request_matches rm
                JOIN users u ON u.id=rm.donor_id
                JOIN donor_profiles d ON d.user_id=rm.donor_id
                JOIN blood_requests br ON br.id=rm.request_id
                LEFT JOIN hospitals h ON h.id=br.hospital_id
                WHERE rm.request_id=? ORDER BY
                    CASE rm.status WHEN 'ACCEPTED' THEN 1 WHEN 'NOTIFIED' THEN 2 ELSE 3 END, rm.match_score DESC
                """;
        List<DonorMatchRow> raw = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long referenceHospitalIdValue = rs.getLong("reference_hospital_id");
                    boolean referenceHospitalIdWasNull = rs.wasNull();
                    raw.add(new DonorMatchRow(rs.getLong("donor_id"), rs.getString("full_name"),
                            BloodGroup.valueOf(rs.getString("blood_group")), rs.getString("district"), rs.getString("phone"),
                            rs.getDouble("match_score"), rs.getString("match_reason"),
                            AvailabilityStatus.valueOf(rs.getString("availability_status")), rs.getInt("verified_donation_count"),
                            referenceHospitalIdWasNull ? null : referenceHospitalIdValue,
                            (Double) rs.getObject("hospital_latitude"), (Double) rs.getObject("hospital_longitude"),
                            MatchStatus.valueOf(rs.getString("match_status")),
                            rs.getTimestamp("donor_confirmed_at") != null, rs.getTimestamp("requester_confirmed_at") != null));
                }
            }
        }
        LocationService locationService = new LocationService();
        Map<Long, ReputationSummary> reputations = new ReviewDAO().reputationsOf(raw.stream().map(DonorMatchRow::donorId).toList());
        List<MatchCandidate> rows = new ArrayList<>();
        for (DonorMatchRow row : raw) {
            Double distanceKm = locationService.distanceKm(row.district(), row.referenceHospitalId(), row.hospitalLat(), row.hospitalLon()).orElse(null);
            ReputationSummary reputation = reputations.getOrDefault(row.donorId(), ReputationSummary.none(row.donorId()));
            rows.add(new MatchCandidate(row.donorId(), row.name(), row.group(), row.district(), row.phone(), row.score(), row.reason(),
                    row.availability(), BadgeTier.fromDonationCount(row.verifiedCount()), distanceKm,
                    reputation.hasReviews() ? reputation.averageRating() : null, reputation.reviewCount(),
                    row.matchStatus(), row.donorConfirmed(), row.requesterConfirmed()));
        }
        return rows;
    }

    private record RequesterMatchRow(long requestId, long requesterId, BloodGroup group, String hospitalName, String district,
                                     Urgency urgency, LocalDate deadline, RequestStatus status, MatchStatus matchStatus,
                                     double score, Double hospitalLat, Double hospitalLon, int unitsNeeded, int unitsFulfilled,
                                     boolean donorConfirmed, boolean requesterConfirmed) { }

    /**
     * Open matches for one donor. A row disappears once EITHER the whole request is
     * FULFILLED/CANCELLED, OR this specific donor's own handshake is already fully
     * confirmed on both sides -- their part is done even if the request is still
     * PARTIALLY_FULFILLED waiting on other donors.
     */
    public List<DonorMatchView> findMatchesForDonor(long donorId, String donorDistrict, Long donorReferenceHospitalId) throws SQLException {
        String sql = """
                SELECT br.id,br.requester_id,br.blood_group,br.hospital_name,br.district,br.urgency,br.deadline,br.status,
                       br.units_needed,br.units_fulfilled,
                       rm.status AS match_status,rm.match_score,rm.donor_confirmed_at,rm.requester_confirmed_at,
                       h.latitude AS hospital_latitude, h.longitude AS hospital_longitude
                FROM request_matches rm
                JOIN blood_requests br ON br.id=rm.request_id
                LEFT JOIN hospitals h ON h.id=br.hospital_id
                WHERE rm.donor_id=? AND br.status NOT IN ('FULFILLED','CANCELLED')
                  AND NOT (rm.donor_confirmed_at IS NOT NULL AND rm.requester_confirmed_at IS NOT NULL)
                ORDER BY CASE br.urgency WHEN 'CRITICAL' THEN 1 WHEN 'URGENT' THEN 2 WHEN 'NORMAL' THEN 3 ELSE 4 END, br.deadline, rm.match_score DESC
                """;
        List<RequesterMatchRow> raw = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, donorId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    raw.add(new RequesterMatchRow(rs.getLong("id"), rs.getLong("requester_id"), BloodGroup.valueOf(rs.getString("blood_group")),
                            rs.getString("hospital_name"), rs.getString("district"), Urgency.valueOf(rs.getString("urgency")),
                            rs.getObject("deadline", LocalDate.class), RequestStatus.valueOf(rs.getString("status")),
                            MatchStatus.valueOf(rs.getString("match_status")), rs.getDouble("match_score"),
                            (Double) rs.getObject("hospital_latitude"), (Double) rs.getObject("hospital_longitude"),
                            rs.getInt("units_needed"), rs.getInt("units_fulfilled"),
                            rs.getTimestamp("donor_confirmed_at") != null, rs.getTimestamp("requester_confirmed_at") != null));
                }
            }
        }
        LocationService locationService = new LocationService();
        Map<Long, ReputationSummary> reputations = new ReviewDAO().reputationsOf(raw.stream().map(RequesterMatchRow::requesterId).toList());
        List<DonorMatchView> rows = new ArrayList<>();
        for (RequesterMatchRow row : raw) {
            Double distanceKm = locationService.distanceKm(donorDistrict, donorReferenceHospitalId, row.hospitalLat(), row.hospitalLon()).orElse(null);
            ReputationSummary reputation = reputations.getOrDefault(row.requesterId(), ReputationSummary.none(row.requesterId()));
            rows.add(new DonorMatchView(row.requestId(), row.group(), row.hospitalName(), row.district(), row.urgency(),
                    row.deadline(), row.status(), row.matchStatus(), row.score(), distanceKm,
                    reputation.hasReviews() ? reputation.averageRating() : null, reputation.reviewCount(),
                    row.unitsNeeded(), row.unitsFulfilled(), row.donorConfirmed(), row.requesterConfirmed()));
        }
        return rows;
    }

    /** Every donor who has ever had a match row for this request, any status -- used to make rematching purely additive. */
    public Set<Long> findMatchedDonorIds(long requestId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            return findMatchedDonorIds(connection, requestId);
        }
    }

    private Set<Long> findMatchedDonorIds(Connection connection, long requestId) throws SQLException {
        Set<Long> ids = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT DISTINCT donor_id FROM request_matches WHERE request_id=?")) {
            statement.setLong(1, requestId);
            try (ResultSet rs = statement.executeQuery()) { while (rs.next()) ids.add(rs.getLong("donor_id")); }
        }
        return ids;
    }

    /**
     * Adds newly-matched donors for a request. Deliberately never deletes or re-notifies
     * a donor who already has a row here (NOTIFIED, ACCEPTED, DECLINED, or EXPIRED) --
     * that would either duplicate a notification for someone already waiting on an
     * answer, or silently re-pester someone who already declined. Re-checks membership
     * under this request's row lock (defensive, in case of a race with another rematch
     * call between MatchingService's own membership check and this one) rather than
     * trusting the caller's list is already filtered.
     */
    public void saveMatches(long requestId, long requesterId, List<MatchCandidate> candidates) throws SQLException {
        String insert = """
                INSERT INTO request_matches(request_id,donor_id,match_score,match_reason,status)
                VALUES(?,?,?,?, 'NOTIFIED')
                """;
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                BloodRequest request = lockRequest(connection, requestId);
                if (request.requesterId() != requesterId) throw new SQLException("You do not own this request.");
                if (!isOpenForMatching(request.status())) {
                    throw new SQLException("This request is not open for (re)matching.");
                }
                Set<Long> alreadyMatched = findMatchedDonorIds(connection, requestId);
                List<MatchCandidate> newCandidates = candidates.stream()
                        .filter(candidate -> !alreadyMatched.contains(candidate.donorId()))
                        .toList();
                for (MatchCandidate candidate : newCandidates) {
                    try (PreparedStatement statement = connection.prepareStatement(insert)) {
                        statement.setLong(1, requestId);
                        statement.setLong(2, candidate.donorId());
                        statement.setDouble(3, candidate.score());
                        statement.setString(4, candidate.reason());
                        statement.executeUpdate();
                    }
                    new NotificationDAO().create(connection, candidate.donorId(), "Urgent blood match",
                            "Request #" + requestId + " matches your profile. Review and respond.", "MATCH", requestId);
                }
                recomputeStatus(connection, requestId);
                if (!newCandidates.isEmpty()) {
                    new NotificationDAO().create(connection, requesterId, "Donors matched",
                            newCandidates.size() + " new eligible donor(s) were notified for request #" + requestId + ".", "MATCH", requestId);
                } else if (candidates.isEmpty()) {
                    new NotificationDAO().create(connection, requesterId, "No eligible donor yet",
                            "Request #" + requestId + " remains open and can be matched again later.", "MATCH", requestId);
                } else {
                    new NotificationDAO().create(connection, requesterId, "No new donors found",
                            "Everyone currently eligible for request #" + requestId + " has already been notified.", "MATCH", requestId);
                }
                new AuditDAO().log(connection, requesterId, "RUN_MATCHING", "BLOOD_REQUEST", requestId,
                        newCandidates.size() + " new candidate(s) saved");
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally { connection.setAutoCommit(true); }
        }
    }

    private boolean isOpenForMatching(RequestStatus status) {
        return status == RequestStatus.PENDING || status == RequestStatus.MATCHED || status == RequestStatus.DECLINED
                || status == RequestStatus.ESCALATED || status == RequestStatus.ACCEPTED || status == RequestStatus.PARTIALLY_FULFILLED;
    }

    /**
     * A donor accepts one unit's worth of a request. Capacity is enforced under the
     * request's row lock: a donor cannot accept if enough units are already fulfilled
     * or reserved by other currently-ACCEPTED donors to cover units_needed. If this
     * acceptance exactly fills the remaining need, any other still-NOTIFIED donors are
     * released (no longer needed) rather than left hanging indefinitely.
     */
    public void acceptMatch(long requestId, long donorId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                BloodRequest request = lockRequest(connection, requestId);
                if (!isOpenForMatching(request.status()))
                    throw new SQLException("This request is not accepting donor responses.");
                long reserved = countByStatus(connection, requestId, "ACCEPTED");
                int remaining = request.unitsNeeded() - request.unitsFulfilled() - (int) reserved;
                if (remaining <= 0)
                    throw new SQLException("This request already has enough donors committed -- no more are needed right now.");
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE request_matches SET status='ACCEPTED',responded_at=CURRENT_TIMESTAMP " +
                                "WHERE request_id=? AND donor_id=? AND status='NOTIFIED'")) {
                    statement.setLong(1, requestId);
                    statement.setLong(2, donorId);
                    if (statement.executeUpdate() == 0) throw new SQLException("This match is expired or was already answered.");
                }
                if (remaining - 1 <= 0) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE request_matches SET status='EXPIRED',responded_at=CURRENT_TIMESTAMP " +
                                    "WHERE request_id=? AND donor_id<>? AND status='NOTIFIED'")) {
                        statement.setLong(1, requestId);
                        statement.setLong(2, donorId);
                        statement.executeUpdate();
                    }
                }
                recomputeStatus(connection, requestId);
                new NotificationDAO().create(connection, request.requesterId(), "Donor accepted",
                        "A donor accepted request #" + requestId + ". Once the donation happens, you'll each confirm it here to complete the record.", "RESPONSE", requestId);
                new NotificationDAO().create(connection, donorId, "Response confirmed",
                        "You accepted request #" + requestId + ". After you donate, confirm it from your Matched Requests tab.", "RESPONSE", requestId);
                new AuditDAO().log(connection, donorId, "ACCEPT_MATCH", "BLOOD_REQUEST", requestId, "Donor accepted match");
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally { connection.setAutoCommit(true); }
        }
    }

    public void declineMatch(long requestId, long donorId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                BloodRequest request = lockRequest(connection, requestId);
                if (!isOpenForMatching(request.status()))
                    throw new SQLException("This request is not accepting donor responses.");
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE request_matches SET status='DECLINED',responded_at=CURRENT_TIMESTAMP WHERE request_id=? AND donor_id=? AND status='NOTIFIED'")) {
                    statement.setLong(1, requestId);
                    statement.setLong(2, donorId);
                    if (statement.executeUpdate() == 0) throw new SQLException("This match is expired or was already answered.");
                }
                recomputeStatus(connection, requestId);
                long remaining = countByStatus(connection, requestId, "NOTIFIED");
                new NotificationDAO().create(connection, request.requesterId(), "Donor declined",
                        remaining == 0
                                ? "A donor declined request #" + requestId + " and no other donors are currently pending. Run matching again or ask an admin to escalate it."
                                : "One donor declined request #" + requestId + ". " + remaining + " response(s) remain pending.",
                        "RESPONSE", requestId);
                new AuditDAO().log(connection, donorId, "DECLINE_MATCH", "BLOOD_REQUEST", requestId,
                        remaining + " notified match(es) remain");
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally { connection.setAutoCommit(true); }
        }
    }

    /** Donor-side half of one donor's handshake for this request. */
    public void confirmDonorSide(long requestId, long donorId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                lockRequest(connection, requestId);
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE request_matches SET donor_confirmed_at=CURRENT_TIMESTAMP " +
                                "WHERE request_id=? AND donor_id=? AND status='ACCEPTED' AND donor_confirmed_at IS NULL")) {
                    statement.setLong(1, requestId);
                    statement.setLong(2, donorId);
                    if (statement.executeUpdate() == 0)
                        throw new SQLException("You have not accepted this request, already confirmed, or it is no longer awaiting confirmation.");
                }
                new AuditDAO().log(connection, donorId, "CONFIRM_DONATED", "BLOOD_REQUEST", requestId, "Donor confirmed the donation happened");
                finalizeIfBothConfirmed(connection, requestId, donorId);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally { connection.setAutoCommit(true); }
        }
    }

    /** Requester-side half of the handshake, scoped to one specific donor's match. */
    public void confirmRequesterSide(long requestId, long requesterId, long donorId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                BloodRequest request = lockRequest(connection, requestId);
                if (request.requesterId() != requesterId) throw new SQLException("You do not own this request.");
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE request_matches SET requester_confirmed_at=CURRENT_TIMESTAMP " +
                                "WHERE request_id=? AND donor_id=? AND status='ACCEPTED' AND requester_confirmed_at IS NULL")) {
                    statement.setLong(1, requestId);
                    statement.setLong(2, donorId);
                    if (statement.executeUpdate() == 0)
                        throw new SQLException("This donor has not accepted, already confirmed, or is no longer awaiting confirmation.");
                }
                new AuditDAO().log(connection, requesterId, "CONFIRM_RECEIVED", "BLOOD_REQUEST", requestId,
                        "Requester confirmed receiving from donor #" + donorId);
                finalizeIfBothConfirmed(connection, requestId, donorId);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally { connection.setAutoCommit(true); }
        }
    }

    /**
     * When BOTH sides of one donor's handshake are in, records that donor's verified
     * donation, updates their stats/cooldown, increments the request's fulfilled-unit
     * count, and recomputes the overall request status. Guarded against double-processing
     * by checking donation_history first, since the two confirm methods above can both
     * (in principle) race to call this for the same donor.
     */
    private void finalizeIfBothConfirmed(Connection connection, long requestId, long donorId) throws SQLException {
        boolean donorConfirmed;
        boolean requesterConfirmed;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT donor_confirmed_at, requester_confirmed_at FROM request_matches WHERE request_id=? AND donor_id=? FOR UPDATE")) {
            statement.setLong(1, requestId);
            statement.setLong(2, donorId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return;
                donorConfirmed = rs.getTimestamp("donor_confirmed_at") != null;
                requesterConfirmed = rs.getTimestamp("requester_confirmed_at") != null;
            }
        }
        if (!donorConfirmed || !requesterConfirmed) return;

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM donation_history WHERE donor_id=? AND request_id=?")) {
            statement.setLong(1, donorId);
            statement.setLong(2, requestId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return; // already finalized for this donor+request
            }
        }

        BloodRequest request = lockRequest(connection, requestId);
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO donation_history(donor_id,request_id,donation_date,hospital_name,blood_group,units,verified) VALUES(?,?,CURRENT_DATE,?,?,1,TRUE)")) {
            statement.setLong(1, donorId);
            statement.setLong(2, requestId);
            statement.setString(3, request.hospitalName());
            statement.setString(4, request.bloodGroup().name());
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE donor_profiles SET last_donation_date=CURRENT_DATE,verified_donation_count=verified_donation_count+1,availability_status='BUSY' WHERE user_id=?")) {
            statement.setLong(1, donorId);
            statement.executeUpdate();
        }
        // This donor just entered their post-donation cooldown. Any OTHER request they
        // were merely NOTIFIED about (never responded) is no longer realistic for them
        // right now -- clear it out rather than leaving a stale, unactionable entry in
        // their list (RequestService.accept() would reject it anyway; this just avoids
        // showing something they can no longer act on). Requests they've already
        // ACCEPTED elsewhere are deliberately left alone -- that's a real commitment
        // already in progress, not something to silently cancel because of this.
        expireOtherNotifiedMatchesForDonor(connection, donorId, requestId);
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE blood_requests SET units_fulfilled=units_fulfilled+1 WHERE id=?")) {
            statement.setLong(1, requestId);
            statement.executeUpdate();
        }
        recomputeStatus(connection, requestId);
        BloodRequest updated = lockRequest(connection, requestId);
        if (updated.status() == RequestStatus.FULFILLED) {
            expireOpenMatches(connection, requestId);
        }
        new NotificationDAO().create(connection, donorId, "Donation verified",
                "Your donation for request #" + requestId + " is confirmed by both sides. Your donation count and cooldown were updated -- you can now rate the requester.",
                "FULFILLED", requestId);
        new NotificationDAO().create(connection, request.requesterId(), "Donation verified",
                "A donor's contribution to request #" + requestId + " is confirmed (" + updated.unitsFulfilled() + " of "
                        + updated.unitsNeeded() + " units so far). You can now rate that donor.", "FULFILLED", requestId);
    }

    public void cancel(long requestId, long requesterId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                BloodRequest request = lockRequest(connection, requestId);
                if (request.requesterId() != requesterId) throw new SQLException("You do not own this request.");
                if (request.status() == RequestStatus.FULFILLED || request.status() == RequestStatus.CANCELLED)
                    throw new SQLException("This request is already closed.");
                updateStatus(connection, requestId, RequestStatus.CANCELLED);
                expireOpenMatches(connection, requestId);
                insertHistory(connection, requestId, request.status(), RequestStatus.CANCELLED, requesterId,
                        "Requester cancelled the request");
                for (Long donorId : findDonorIdsByStatus(connection, requestId, "ACCEPTED")) {
                    new NotificationDAO().create(connection, donorId, "Request cancelled",
                            "The requester cancelled request #" + requestId + ".", "CANCELLED", requestId);
                }
                new AuditDAO().log(connection, requesterId, "CANCEL_REQUEST", "BLOOD_REQUEST", requestId,
                        "Requester cancelled the request");
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally { connection.setAutoCommit(true); }
        }
    }

    public void adminTransition(long requestId, long adminId, RequestStatus target, String note) throws SQLException {
        if (target != RequestStatus.ESCALATED && target != RequestStatus.CANCELLED)
            throw new IllegalArgumentException("Unsupported admin transition.");
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                BloodRequest request = lockRequest(connection, requestId);
                if (request.status() == RequestStatus.FULFILLED || request.status() == RequestStatus.CANCELLED)
                    throw new SQLException("Closed requests cannot be changed.");
                if (target == RequestStatus.ESCALATED && (request.status() == RequestStatus.ACCEPTED || request.status() == RequestStatus.PARTIALLY_FULFILLED))
                    throw new SQLException("This request already has an active donor and does not need escalation.");
                updateStatus(connection, requestId, target);
                if (target == RequestStatus.CANCELLED) expireOpenMatches(connection, requestId);
                insertHistory(connection, requestId, request.status(), target, adminId, note);
                new NotificationDAO().create(connection, request.requesterId(), "Request updated by admin",
                        "Request #" + requestId + " is now " + target + ".", "ADMIN", requestId);
                if (target == RequestStatus.CANCELLED) {
                    for (Long donorId : findDonorIdsByStatus(connection, requestId, "ACCEPTED")) {
                        new NotificationDAO().create(connection, donorId, "Request closed by admin",
                                "Request #" + requestId + " was closed by an administrator.", "ADMIN", requestId);
                    }
                }
                new AuditDAO().log(connection, adminId, "ADMIN_" + target, "BLOOD_REQUEST", requestId, note);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally { connection.setAutoCommit(true); }
        }
    }

    /**
     * Derives and, if needed, writes the correct overall status from real data,
     * instead of hand-tracking every transition: FULFILLED once enough units are
     * confirmed, PARTIALLY_FULFILLED once at least one is, ACCEPTED while donors are
     * committed but none has completed yet, MATCHED while donors are still pending,
     * DECLINED once every avenue is exhausted with nothing accepted. Never touches a
     * request that is already CANCELLED -- that is a deliberate terminal state.
     */
    private void recomputeStatus(Connection connection, long requestId) throws SQLException {
        BloodRequest current = lockRequest(connection, requestId);
        if (current.status() == RequestStatus.CANCELLED) return;
        RequestStatus newStatus;
        if (current.isFullyFulfilled()) {
            newStatus = RequestStatus.FULFILLED;
        } else if (current.unitsFulfilled() > 0) {
            newStatus = RequestStatus.PARTIALLY_FULFILLED;
        } else if (countByStatus(connection, requestId, "ACCEPTED") > 0) {
            newStatus = RequestStatus.ACCEPTED;
        } else if (countByStatus(connection, requestId, "NOTIFIED") > 0) {
            newStatus = RequestStatus.MATCHED;
        } else if (current.status() == RequestStatus.PENDING) {
            newStatus = RequestStatus.PENDING; // never matched yet -- leave alone
        } else {
            newStatus = RequestStatus.DECLINED;
        }
        if (newStatus != current.status()) {
            updateStatus(connection, requestId, newStatus);
            insertHistory(connection, requestId, current.status(), newStatus, null, "Status recalculated from match/fulfillment state");
        }
    }

    private long countByStatus(Connection connection, long requestId, String matchStatus) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM request_matches WHERE request_id=? AND status=?")) {
            statement.setLong(1, requestId);
            statement.setString(2, matchStatus);
            try (ResultSet rs = statement.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }

    /**
     * Clears every OTHER request this donor was merely NOTIFIED about (never
     * responded) once they enter cooldown -- and, since removing those matches can
     * change what each affected request's own status should be (e.g. it may have
     * been the last pending candidate), recomputes status for every affected
     * request too rather than leaving them stuck showing stale state.
     */
    private void expireOtherNotifiedMatchesForDonor(Connection connection, long donorId, long excludeRequestId) throws SQLException {
        List<Long> affectedRequestIds = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT request_id FROM request_matches WHERE donor_id=? AND request_id<>? AND status='NOTIFIED'")) {
            select.setLong(1, donorId);
            select.setLong(2, excludeRequestId);
            try (ResultSet rs = select.executeQuery()) { while (rs.next()) affectedRequestIds.add(rs.getLong("request_id")); }
        }
        if (affectedRequestIds.isEmpty()) return;
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE request_matches SET status='EXPIRED', responded_at=CURRENT_TIMESTAMP " +
                        "WHERE donor_id=? AND request_id<>? AND status='NOTIFIED'")) {
            update.setLong(1, donorId);
            update.setLong(2, excludeRequestId);
            update.executeUpdate();
        }
        for (Long affectedRequestId : affectedRequestIds) {
            recomputeStatus(connection, affectedRequestId);
        }
    }

    private List<Long> findDonorIdsByStatus(Connection connection, long requestId, String matchStatus) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT donor_id FROM request_matches WHERE request_id=? AND status=?")) {
            statement.setLong(1, requestId);
            statement.setString(2, matchStatus);
            try (ResultSet rs = statement.executeQuery()) { while (rs.next()) ids.add(rs.getLong("donor_id")); }
        }
        return ids;
    }

    /**
     * Releases matches that are no longer relevant when a request is being shut down
     * (cancelled) or is now fully covered: NOTIFIED (never accepted) and ACCEPTED matches
     * where NEITHER side has confirmed yet. Deliberately leaves alone any match where one
     * side already confirmed -- if a donor already gave blood and confirmed it, that
     * handshake is allowed to complete even if the request closes around it.
     */
    private void expireOpenMatches(Connection connection, long requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE request_matches SET status='EXPIRED',responded_at=CURRENT_TIMESTAMP " +
                        "WHERE request_id=? AND status IN ('NOTIFIED','ACCEPTED') " +
                        "AND donor_confirmed_at IS NULL AND requester_confirmed_at IS NULL")) {
            statement.setLong(1, requestId);
            statement.executeUpdate();
        }
    }

    private BloodRequest lockRequest(Connection connection, long requestId) throws SQLException {
        String sql = baseSelect() + " WHERE br.id=? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new SQLException("Request not found.");
                return mapRequest(rs);
            }
        }
    }

    private void updateStatus(Connection connection, long requestId, RequestStatus status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE blood_requests SET status=? WHERE id=?")) {
            statement.setString(1, status.name());
            statement.setLong(2, requestId);
            statement.executeUpdate();
        }
    }

    private void insertHistory(Connection connection, long requestId, RequestStatus from, RequestStatus to,
                               Long changedBy, String note) throws SQLException {
        String sql = "INSERT INTO request_status_history(request_id,from_status,to_status,changed_by,note) VALUES(?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            if (from == null) statement.setNull(2, Types.VARCHAR); else statement.setString(2, from.name());
            statement.setString(3, to.name());
            if (changedBy == null) statement.setNull(4, Types.BIGINT); else statement.setLong(4, changedBy);
            statement.setString(5, note);
            statement.executeUpdate();
        }
    }

    private String baseSelect() {
        return """
                SELECT br.id,br.requester_id,u.full_name AS requester_name,br.blood_group,br.units_needed,br.units_fulfilled,
                       br.urgency,br.hospital_name,br.hospital_id,h.latitude AS hospital_latitude,h.longitude AS hospital_longitude,
                       br.district,br.deadline,br.notes,br.status,
                       br.accepted_donor_id,br.donor_confirmed_at,br.requester_confirmed_at,br.created_at,br.updated_at
                FROM blood_requests br
                JOIN users u ON u.id=br.requester_id
                LEFT JOIN hospitals h ON h.id=br.hospital_id
                """;
    }

    private BloodRequest mapRequest(ResultSet rs) throws SQLException {
        long donorId = rs.getLong("accepted_donor_id");
        boolean donorIdWasNull = rs.wasNull();
        long hospitalIdValue = rs.getLong("hospital_id");
        boolean hospitalIdWasNull = rs.wasNull();
        Timestamp donorConfirmed = rs.getTimestamp("donor_confirmed_at");
        Timestamp requesterConfirmed = rs.getTimestamp("requester_confirmed_at");
        return new BloodRequest(rs.getLong("id"), rs.getLong("requester_id"), rs.getString("requester_name"),
                BloodGroup.valueOf(rs.getString("blood_group")), rs.getInt("units_needed"), rs.getInt("units_fulfilled"),
                Urgency.valueOf(rs.getString("urgency")), rs.getString("hospital_name"),
                hospitalIdWasNull ? null : hospitalIdValue,
                (Double) rs.getObject("hospital_latitude"), (Double) rs.getObject("hospital_longitude"),
                rs.getString("district"), rs.getObject("deadline", LocalDate.class), rs.getString("notes"),
                RequestStatus.valueOf(rs.getString("status")),
                donorIdWasNull ? null : donorId,
                donorConfirmed == null ? null : donorConfirmed.toLocalDateTime(),
                requesterConfirmed == null ? null : requesterConfirmed.toLocalDateTime(),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime());
    }
}
