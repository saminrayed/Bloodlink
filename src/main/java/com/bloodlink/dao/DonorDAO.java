package com.bloodlink.dao;

import com.bloodlink.model.*;
import com.bloodlink.util.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class DonorDAO {
    public List<Donor> findAvailableDonors() throws SQLException {
        String sql = """
                SELECT u.id, u.full_name, u.email, u.phone, u.district, u.address, u.approved, u.active, u.created_at,
                       d.blood_group, d.birth_date, d.weight_kg, d.last_donation_date,
                       d.availability_status, d.verified_donation_count, d.reference_hospital_id
                FROM users u JOIN donor_profiles d ON d.user_id=u.id
                WHERE u.role='DONOR' AND u.approved=TRUE AND u.active=TRUE AND d.availability_status='AVAILABLE'
                """;
        List<Donor> donors = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) donors.add(mapDonor(rs));
        }
        return donors;
    }

    public void updateAvailability(long donorId, AvailabilityStatus status) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE donor_profiles SET availability_status=? WHERE user_id=?")) {
                statement.setString(1, status.name());
                statement.setLong(2, donorId);
                if (statement.executeUpdate() == 0) throw new SQLException("Donor profile not found.");
                new AuditDAO().log(connection, donorId, "UPDATE_AVAILABILITY", "DONOR_PROFILE", donorId,
                        "availability=" + status.name());
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void updateHealthProfile(long donorId, double weightKg, LocalDate lastDonationDate) throws SQLException {
        String sql = "UPDATE donor_profiles SET weight_kg=?, last_donation_date=? WHERE user_id=?";
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setDouble(1, weightKg);
                statement.setObject(2, lastDonationDate);
                statement.setLong(3, donorId);
                if (statement.executeUpdate() == 0) throw new SQLException("Donor profile not found.");
                new AuditDAO().log(connection, donorId, "UPDATE_HEALTH_PROFILE", "DONOR_PROFILE", donorId,
                        "Weight and last donation date updated");
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /**
     * Sets or clears the donor's chosen reference hospital -- the precise, optional
     * stand-in for their location described on {@link com.bloodlink.model.Donor}.
     * {@code hospitalId} may be null to clear it and fall back to the district default.
     */
    public void updateReferenceHospital(long donorId, Long hospitalId) throws SQLException {
        String sql = "UPDATE donor_profiles SET reference_hospital_id=? WHERE user_id=?";
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                if (hospitalId == null) statement.setNull(1, Types.BIGINT); else statement.setLong(1, hospitalId);
                statement.setLong(2, donorId);
                if (statement.executeUpdate() == 0) throw new SQLException("Donor profile not found.");
                new AuditDAO().log(connection, donorId, "UPDATE_REFERENCE_HOSPITAL", "DONOR_PROFILE", donorId,
                        hospitalId == null ? "Cleared reference hospital" : "Reference hospital set to #" + hospitalId);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public List<DonationRecord> findDonationHistory(long donorId) throws SQLException {
        String sql = """
                SELECT id, donor_id, request_id, donation_date, hospital_name, blood_group, units, verified
                FROM donation_history WHERE donor_id=? ORDER BY donation_date DESC
                """;
        List<DonationRecord> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, donorId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long requestId = rs.getLong("request_id");
                    rows.add(new DonationRecord(rs.getLong("id"), rs.getLong("donor_id"), rs.wasNull() ? null : requestId,
                            rs.getObject("donation_date", LocalDate.class), rs.getString("hospital_name"),
                            BloodGroup.valueOf(rs.getString("blood_group")), rs.getInt("units"), rs.getBoolean("verified")));
                }
            }
        }
        return rows;
    }

    private Donor mapDonor(ResultSet rs) throws SQLException {
        long referenceHospitalIdValue = rs.getLong("reference_hospital_id");
        boolean referenceHospitalIdWasNull = rs.wasNull();
        return new Donor(rs.getLong("id"), rs.getString("full_name"), rs.getString("email"),
                rs.getString("phone"), rs.getString("district"), rs.getString("address"),
                rs.getBoolean("approved"), rs.getBoolean("active"), rs.getTimestamp("created_at").toLocalDateTime(),
                BloodGroup.valueOf(rs.getString("blood_group")), rs.getObject("birth_date", LocalDate.class),
                rs.getDouble("weight_kg"), rs.getObject("last_donation_date", LocalDate.class),
                AvailabilityStatus.valueOf(rs.getString("availability_status")), rs.getInt("verified_donation_count"),
                referenceHospitalIdWasNull ? null : referenceHospitalIdValue);
    }
}
