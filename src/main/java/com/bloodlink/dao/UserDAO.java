package com.bloodlink.dao;

import com.bloodlink.model.*;
import com.bloodlink.util.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

public final class UserDAO {
    private static final String USER_SELECT = """
            SELECT u.id, u.full_name, u.email, u.password_hash, u.phone, u.district, u.address,
                   u.role, u.approved, u.active, u.created_at,
                   d.blood_group, d.birth_date, d.weight_kg, d.last_donation_date,
                   d.availability_status, d.verified_donation_count, d.reference_hospital_id
            FROM users u
            LEFT JOIN donor_profiles d ON d.user_id = u.id
            """;

    public Optional<User> findByEmail(String email) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(USER_SELECT + " WHERE LOWER(u.email) = LOWER(?)")) {
            statement.setString(1, email.trim());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapUser(rs)) : Optional.empty();
            }
        }
    }

    public Optional<User> findById(long id) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(USER_SELECT + " WHERE u.id = ?")) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapUser(rs)) : Optional.empty();
            }
        }
    }

    public String findPasswordHash(long userId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT password_hash FROM users WHERE id = ?")) {
            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new SQLException("User not found.");
                return rs.getString(1);
            }
        }
    }

    public boolean emailExists(String email) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM users WHERE LOWER(email)=LOWER(?)")) {
            statement.setString(1, email.trim());
            try (ResultSet rs = statement.executeQuery()) { return rs.next(); }
        }
    }

    /**
     * Deliberately separate from USER_SELECT above: photo bytes are never part of the
     * routine user/donor fetch used for matching, search, or dashboard lists -- only
     * called once, explicitly, when a specific profile is actually being displayed.
     */
    public Optional<byte[]> findPhoto(long userId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT photo FROM users WHERE id=?")) {
            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.ofNullable(rs.getBytes("photo"));
            }
        }
    }

    /** Pass null to remove an existing photo. Size limits are enforced by the caller (ProfileService), not here. */
    public void updatePhoto(long userId, byte[] photoBytes) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("UPDATE users SET photo=? WHERE id=?")) {
                if (photoBytes == null) statement.setNull(1, Types.BLOB); else statement.setBytes(1, photoBytes);
                statement.setLong(2, userId);
                if (statement.executeUpdate() == 0) throw new SQLException("User not found.");
                new AuditDAO().log(connection, userId, "UPDATE_PHOTO", "USER", userId,
                        photoBytes == null ? "Photo removed" : "Photo updated (" + photoBytes.length + " bytes)");
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public long register(RegistrationData data, String passwordHash) throws SQLException {
        String userSql = """
                INSERT INTO users(full_name, email, password_hash, phone, district, address, role, approved, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)
                """;
        String donorSql = """
                INSERT INTO donor_profiles(user_id, blood_group, birth_date, weight_kg, last_donation_date,
                                           availability_status, verified_donation_count)
                VALUES (?, ?, ?, ?, ?, 'BUSY', 0)
                """;
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement statement = connection.prepareStatement(userSql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, data.fullName().trim());
                    statement.setString(2, data.email().trim().toLowerCase());
                    statement.setString(3, passwordHash);
                    statement.setString(4, data.phone().trim());
                    statement.setString(5, data.district().trim());
                    statement.setString(6, data.address() == null ? "" : data.address().trim());
                    statement.setString(7, data.role().name());
                    statement.setBoolean(8, data.role() == Role.REQUESTER);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Registration did not return a user ID.");
                        id = keys.getLong(1);
                    }
                }
                if (data.role() == Role.DONOR) {
                    try (PreparedStatement statement = connection.prepareStatement(donorSql)) {
                        statement.setLong(1, id);
                        statement.setString(2, data.bloodGroup().name());
                        statement.setObject(3, data.birthDate());
                        statement.setDouble(4, data.weightKg());
                        statement.setObject(5, data.lastDonationDate());
                        statement.executeUpdate();
                    }
                }
                new AuditDAO().log(connection, id, "REGISTER", "USER", id, "Registered as " + data.role());
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

    public void updateProfile(long userId, String fullName, String phone, String district, String address) throws SQLException {
        String sql = "UPDATE users SET full_name=?, phone=?, district=?, address=? WHERE id=?";
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, fullName.trim());
                statement.setString(2, phone.trim());
                statement.setString(3, district.trim());
                statement.setString(4, address == null ? "" : address.trim());
                statement.setLong(5, userId);
                if (statement.executeUpdate() == 0) throw new SQLException("User profile not found.");
                new AuditDAO().log(connection, userId, "UPDATE_PROFILE", "USER", userId, "Contact profile updated");
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void updatePassword(long userId, String passwordHash) throws SQLException {
        updatePassword(userId, passwordHash, userId, "CHANGE_PASSWORD");
    }

    public void resetPassword(long userId, String passwordHash, long actorId) throws SQLException {
        updatePassword(userId, passwordHash, actorId, "ADMIN_RESET_PASSWORD");
    }

    private void updatePassword(long userId, String passwordHash, long actorId, String action) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("UPDATE users SET password_hash=? WHERE id=?")) {
                statement.setString(1, passwordHash);
                statement.setLong(2, userId);
                if (statement.executeUpdate() == 0) throw new SQLException("User not found.");
                new AuditDAO().log(connection, actorId, action, "USER", userId, "Password hash replaced");
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void setApproved(long userId, boolean approved, long actorId) throws SQLException {
        updateFlag(userId, "approved", approved, actorId, approved ? "APPROVE_USER" : "REVOKE_APPROVAL");
    }

    public void setActive(long userId, boolean active, long actorId) throws SQLException {
        updateFlag(userId, "active", active, actorId, active ? "ACTIVATE_USER" : "SUSPEND_USER");
    }

    private void updateFlag(long userId, String column, boolean value, long actorId, String action) throws SQLException {
        String sql = "UPDATE users SET " + column + "=? WHERE id=? AND role <> 'ADMIN'";
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setBoolean(1, value);
                statement.setLong(2, userId);
                int changed = statement.executeUpdate();
                if (changed == 0) throw new SQLException("User could not be updated.");
                new AuditDAO().log(connection, actorId, action, "USER", userId, column + "=" + value);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private User mapUser(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String name = rs.getString("full_name");
        String email = rs.getString("email");
        String phone = rs.getString("phone");
        String district = rs.getString("district");
        String address = rs.getString("address");
        Role role = Role.valueOf(rs.getString("role"));
        boolean approved = rs.getBoolean("approved");
        boolean active = rs.getBoolean("active");
        LocalDateTime created = rs.getTimestamp("created_at").toLocalDateTime();
        return switch (role) {
            case DONOR -> {
                long referenceHospitalIdValue = rs.getLong("reference_hospital_id");
                boolean referenceHospitalIdWasNull = rs.wasNull();
                yield new Donor(id, name, email, phone, district, address, approved, active, created,
                        BloodGroup.valueOf(rs.getString("blood_group")), rs.getObject("birth_date", LocalDate.class),
                        rs.getDouble("weight_kg"), rs.getObject("last_donation_date", LocalDate.class),
                        AvailabilityStatus.valueOf(rs.getString("availability_status")),
                        rs.getInt("verified_donation_count"),
                        referenceHospitalIdWasNull ? null : referenceHospitalIdValue);
            }
            case REQUESTER -> new Requester(id, name, email, phone, district, address, approved, active, created);
            case ADMIN -> new Admin(id, name, email, phone, district, address, approved, active, created);
        };
    }
}
