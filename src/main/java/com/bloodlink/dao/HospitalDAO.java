package com.bloodlink.dao;

import com.bloodlink.model.Hospital;
import com.bloodlink.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class HospitalDAO {

    /**
     * Prefix search across name and district, e.g. for a searchable ComboBox.
     * Uses a leading (not wrapped) wildcard so the name index can be used --
     * intentional, since a full fuzzy search would need a FULLTEXT index this
     * dataset's size does not justify.
     */
    public List<Hospital> search(String query, int limit) throws SQLException {
        String prefix = (query == null ? "" : query.trim()) + "%";
        String sql = """
                SELECT id,name,district,area,address,latitude,longitude,phone,active
                FROM hospitals
                WHERE active=TRUE AND (name LIKE ? OR district LIKE ?)
                ORDER BY name
                LIMIT ?
                """;
        List<Hospital> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, prefix);
            statement.setString(2, prefix);
            statement.setInt(3, Math.max(1, Math.min(limit, 50)));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) rows.add(mapHospital(rs));
            }
        }
        return rows;
    }

    public List<Hospital> findAll() throws SQLException {
        String sql = """
                SELECT id,name,district,area,address,latitude,longitude,phone,active
                FROM hospitals WHERE active=TRUE ORDER BY district,name
                """;
        List<Hospital> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) rows.add(mapHospital(rs));
        }
        return rows;
    }

    public Optional<Hospital> findById(long id) throws SQLException {
        String sql = "SELECT id,name,district,area,address,latitude,longitude,phone,active FROM hospitals WHERE id=?";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapHospital(rs)) : Optional.empty();
            }
        }
    }

    /**
     * The coordinate used to approximate a donor's location: the first active
     * hospital seeded for that district. Real data, coarse precision -- see
     * LocationService for how this is labelled to the user.
     */
    public Optional<double[]> findDistrictReferencePoint(String district) throws SQLException {
        if (district == null || district.isBlank()) return Optional.empty();
        String sql = "SELECT latitude,longitude FROM hospitals WHERE active=TRUE AND district=? ORDER BY id ASC LIMIT 1";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, district.trim());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new double[]{rs.getDouble("latitude"), rs.getDouble("longitude")});
            }
        }
    }

    private Hospital mapHospital(ResultSet rs) throws SQLException {
        return new Hospital(rs.getLong("id"), rs.getString("name"), rs.getString("district"), rs.getString("area"),
                rs.getString("address"), rs.getDouble("latitude"), rs.getDouble("longitude"),
                rs.getString("phone"), rs.getBoolean("active"));
    }
}
