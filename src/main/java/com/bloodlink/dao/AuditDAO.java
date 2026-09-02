package com.bloodlink.dao;

import com.bloodlink.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class AuditDAO {
    public void log(Long actorUserId, String action, String entityType, Long entityId, String details) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            log(connection, actorUserId, action, entityType, entityId, details);
        }
    }

    public void log(Connection connection, Long actorUserId, String action, String entityType,
                    Long entityId, String details) throws SQLException {
        String sql = "INSERT INTO audit_logs(actor_user_id, action, entity_type, entity_id, details) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (actorUserId == null) statement.setNull(1, java.sql.Types.BIGINT); else statement.setLong(1, actorUserId);
            statement.setString(2, action);
            statement.setString(3, entityType);
            if (entityId == null) statement.setNull(4, java.sql.Types.BIGINT); else statement.setLong(4, entityId);
            statement.setString(5, details);
            statement.executeUpdate();
        }
    }
}
