// ============================================================================
// Patch 3c for DatabaseSetup.java: demo data for the features built this
// session. Optional, like 3b -- only matters for a FRESH install (your
// current database already has real data, so this doesn't apply to you
// unless you're setting up a second, separate demo environment).
//
// HOW TO APPLY:
// 1. Add this entire method to DatabaseSetup.java (anywhere among the other
//    private static methods, e.g. right after seedDemoData()).
// 2. In seedDemoData(), inside the existing
//    "if (!demoRequestsExist(connection, requesterId)) { ... }" block, add
//    ONE line immediately before the existing "insertAudit(connection,
//    adminId, ..." call:
//
//        seedAdvancedFeatureDemoData(connection, requesterId, donorOPos, donorONeg, donorAPos, donorANeg);
//
//    (donorOPos, donorONeg, donorAPos, donorANeg are the same local
//    variables already declared earlier in seedDemoData() -- no new ones
//    needed.)
//
// WHAT THIS ADDS, once applied and DB_AUTO_INIT is used for a fresh install:
//   - Sets one donor's reference_hospital_id (Dhaka Medical College
//     Hospital, if migration_002's seed data is present -- skipped
//     gracefully if not, never fails the rest of seeding).
//   - A 3-unit request sitting at PARTIALLY_FULFILLED: one donor's
//     handshake fully confirmed (counts toward units_fulfilled), one
//     donor ACCEPTED but awaiting confirmation (shows the "Waiting on
//     you"/"Waiting on donor" handshake states), one donor still just
//     NOTIFIED.
//   - A second FULFILLED request with a genuine mutual review already
//     submitted on both sides, so the reputation system has something to
//     display without you having to manually complete a full handshake
//     and review cycle by hand first.
// ============================================================================

private static void seedAdvancedFeatureDemoData(Connection connection, long requesterId,
                                                 long donorOPos, long donorONeg, long donorAPos, long donorANeg) throws SQLException {
    // Reference hospital for one donor -- only if migration_002's seed data is present.
    Long dhakaMedicalId = findHospitalId(connection, "Dhaka Medical College Hospital", "Dhaka");
    if (dhakaMedicalId != null) {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE donor_profiles SET reference_hospital_id=? WHERE user_id=?")) {
            statement.setLong(1, dhakaMedicalId);
            statement.setLong(2, donorOPos);
            statement.executeUpdate();
        }
    }

    // --- Multi-donor request: 3 units needed, 1 confirmed, 1 accepted-pending, 1 notified ---
    long multiDonorRequest = insertRequest(connection, requesterId, BloodGroup.O_POSITIVE, 3, Urgency.URGENT,
            "Evercare Hospital Dhaka", "Dhaka", LocalDate.now().plusDays(3),
            "[DEMO] Multi-donor partial fulfillment example", RequestStatus.PARTIALLY_FULFILLED, null);
    insertHistory(connection, multiDonorRequest, null, RequestStatus.PENDING, requesterId, "Demo request submitted");
    insertHistory(connection, multiDonorRequest, RequestStatus.PENDING, RequestStatus.MATCHED, requesterId, "Eligible donors ranked");
    insertHistory(connection, multiDonorRequest, RequestStatus.MATCHED, RequestStatus.PARTIALLY_FULFILLED, requesterId, "First donor confirmed");

    Timestamp twoHoursAgo = Timestamp.valueOf(java.time.LocalDateTime.now().minusHours(2));
    try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO request_matches(request_id,donor_id,match_score,match_reason,status,responded_at,donor_confirmed_at,requester_confirmed_at) " +
                    "VALUES(?,?,?,?,'ACCEPTED',?,?,?)")) {
        statement.setLong(1, multiDonorRequest);
        statement.setLong(2, donorOPos);
        statement.setDouble(3, 97.0);
        statement.setString(4, "exact blood group, same district, cooldown complete");
        statement.setTimestamp(5, twoHoursAgo);
        statement.setTimestamp(6, twoHoursAgo);
        statement.setTimestamp(7, twoHoursAgo);
        statement.executeUpdate();
    }
    insertDonation(connection, donorOPos, multiDonorRequest, LocalDate.now().minusDays(1),
            "Evercare Hospital Dhaka", BloodGroup.O_POSITIVE, 1);
    try (PreparedStatement statement = connection.prepareStatement("UPDATE blood_requests SET units_fulfilled=1 WHERE id=?")) {
        statement.setLong(1, multiDonorRequest);
        statement.executeUpdate();
    }

    try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO request_matches(request_id,donor_id,match_score,match_reason,status,responded_at) VALUES(?,?,?,?,'ACCEPTED',?)")) {
        statement.setLong(1, multiDonorRequest);
        statement.setLong(2, donorONeg);
        statement.setDouble(3, 88.0);
        statement.setString(4, "compatible blood group, same district");
        statement.setTimestamp(5, Timestamp.valueOf(java.time.LocalDateTime.now().minusMinutes(30)));
        statement.executeUpdate();
    }

    insertMatch(connection, multiDonorRequest, donorAPos, 74.0, "compatible blood group, different district", MatchStatus.NOTIFIED);
    insertNotification(connection, donorAPos, "Urgent blood match",
            "Demo request #" + multiDonorRequest + " matches your profile.", "MATCH", multiDonorRequest);

    // --- A fulfilled request with a real mutual review already on it ---
    long reviewedRequest = insertRequest(connection, requesterId, BloodGroup.A_NEGATIVE, 1, Urgency.NORMAL,
            "Square Hospital", "Dhaka", LocalDate.now().minusDays(10),
            "[DEMO] Completed request with mutual review", RequestStatus.FULFILLED, null);
    insertHistory(connection, reviewedRequest, null, RequestStatus.PENDING, requesterId, "Demo request submitted");
    insertHistory(connection, reviewedRequest, RequestStatus.PENDING, RequestStatus.MATCHED, requesterId, "Eligible donors ranked");
    insertHistory(connection, reviewedRequest, RequestStatus.MATCHED, RequestStatus.FULFILLED, requesterId, "Both parties confirmed the donation");

    Timestamp confirmedAt = Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(9));
    try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO request_matches(request_id,donor_id,match_score,match_reason,status,responded_at,donor_confirmed_at,requester_confirmed_at) " +
                    "VALUES(?,?,?,?,'ACCEPTED',?,?,?)")) {
        statement.setLong(1, reviewedRequest);
        statement.setLong(2, donorANeg);
        statement.setDouble(3, 99.0);
        statement.setString(4, "exact blood group, same district, cooldown complete");
        statement.setTimestamp(5, confirmedAt);
        statement.setTimestamp(6, confirmedAt);
        statement.setTimestamp(7, confirmedAt);
        statement.executeUpdate();
    }
    insertDonation(connection, donorANeg, reviewedRequest, LocalDate.now().minusDays(9), "Square Hospital", BloodGroup.A_NEGATIVE, 1);
    try (PreparedStatement statement = connection.prepareStatement("UPDATE blood_requests SET units_fulfilled=1 WHERE id=?")) {
        statement.setLong(1, reviewedRequest);
        statement.executeUpdate();
    }

    try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO reviews(request_id,reviewer_id,reviewed_id,rating,tags,comment) VALUES(?,?,?,?,?,?)")) {
        statement.setLong(1, reviewedRequest);
        statement.setLong(2, requesterId);
        statement.setLong(3, donorANeg);
        statement.setInt(4, 5);
        statement.setString(5, "RELIABLE,PUNCTUAL");
        statement.setString(6, "Arrived quickly and was very kind throughout.");
        statement.executeUpdate();
    }
    try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO reviews(request_id,reviewer_id,reviewed_id,rating,tags,comment) VALUES(?,?,?,?,?,?)")) {
        statement.setLong(1, reviewedRequest);
        statement.setLong(2, donorANeg);
        statement.setLong(3, requesterId);
        statement.setInt(4, 5);
        statement.setString(5, "RESPONSIVE,COOPERATIVE");
        statement.setString(6, "Clear communication and a smooth process.");
        statement.executeUpdate();
    }
}

/** Returns null (not an exception) if migration_002's hospital seed data isn't present -- this demo addition must never fail the rest of seeding over it. */
private static Long findHospitalId(Connection connection, String name, String district) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
            "SELECT id FROM hospitals WHERE name=? AND district=?")) {
        statement.setString(1, name);
        statement.setString(2, district);
        try (ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : null;
        }
    } catch (SQLException e) {
        // hospitals table doesn't exist yet (migration_002 not applied) -- treat as "not found", don't fail seeding.
        return null;
    }
}
