package com.bloodlink.dao;

import com.bloodlink.model.Review;
import com.bloodlink.model.ReviewTag;
import com.bloodlink.model.ReputationSummary;
import com.bloodlink.util.DBConnection;

import java.sql.*;
import java.util.*;

public final class ReviewDAO {

    public boolean hasReviewed(long requestId, long reviewerId) throws SQLException {
        String sql = "SELECT 1 FROM reviews WHERE request_id=? AND reviewer_id=?";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            statement.setLong(2, reviewerId);
            try (ResultSet rs = statement.executeQuery()) { return rs.next(); }
        }
    }

    public void create(Connection connection, long requestId, long reviewerId, long reviewedId, int rating,
                       List<ReviewTag> tags, String comment) throws SQLException {
        String sql = "INSERT INTO reviews(request_id, reviewer_id, reviewed_id, rating, tags, comment) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            statement.setLong(2, reviewerId);
            statement.setLong(3, reviewedId);
            statement.setInt(4, rating);
            statement.setString(5, tagsToString(tags));
            statement.setString(6, comment == null ? "" : comment.trim());
            statement.executeUpdate();
        }
    }

    public ReputationSummary reputationOf(long userId) throws SQLException {
        String sql = "SELECT AVG(rating) avg_rating, COUNT(*) review_count FROM reviews WHERE reviewed_id=?";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return toSummary(userId, rs);
            }
        }
    }

    /**
     * Reputation for many users in one round trip -- used before scoring/ranking a
     * pool of donors so matching never does one reputation query per donor.
     * Users with no reviews are simply absent from the returned map; callers should
     * treat a missing key the same as {@link ReputationSummary#none(long)}.
     */
    public Map<Long, ReputationSummary> reputationsOf(Collection<Long> userIds) throws SQLException {
        Map<Long, ReputationSummary> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) return result;
        String placeholders = String.join(",", Collections.nCopies(userIds.size(), "?"));
        String sql = "SELECT reviewed_id, AVG(rating) avg_rating, COUNT(*) review_count FROM reviews " +
                "WHERE reviewed_id IN (" + placeholders + ") GROUP BY reviewed_id";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (Long id : userIds) statement.setLong(index++, id);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long userId = rs.getLong("reviewed_id");
                    result.put(userId, toSummary(userId, rs));
                }
            }
        }
        return result;
    }

    public List<Review> findReceivedBy(long userId, int limit) throws SQLException {
        String sql = """
                SELECT r.id, r.request_id, r.reviewer_id, u.full_name reviewer_name, r.reviewed_id,
                       r.rating, r.tags, r.comment, r.created_at
                FROM reviews r JOIN users u ON u.id = r.reviewer_id
                WHERE r.reviewed_id = ? ORDER BY r.created_at DESC LIMIT ?
                """;
        List<Review> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) rows.add(mapReview(rs));
            }
        }
        return rows;
    }

    /**
     * Every request id this reviewer has already submitted a review for -- fetched
     * once so a UI table can check membership in memory per row instead of issuing
     * one "have I reviewed this?" query per row it renders.
     */
    public Set<Long> reviewedRequestIdsBy(long reviewerId) throws SQLException {
        String sql = "SELECT request_id FROM reviews WHERE reviewer_id=?";
        Set<Long> ids = new HashSet<>();
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, reviewerId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong("request_id"));
            }
        }
        return ids;
    }

    private ReputationSummary toSummary(long userId, ResultSet rs) throws SQLException {
        long count = rs.getLong("review_count");
        if (count == 0) return ReputationSummary.none(userId);
        double avg = rs.getDouble("avg_rating");
        return new ReputationSummary(userId, Math.round(avg * 10.0) / 10.0, count);
    }

    private Review mapReview(ResultSet rs) throws SQLException {
        return new Review(rs.getLong("id"), rs.getLong("request_id"), rs.getLong("reviewer_id"), rs.getString("reviewer_name"),
                rs.getLong("reviewed_id"), rs.getInt("rating"), tagsFromString(rs.getString("tags")),
                rs.getString("comment"), rs.getTimestamp("created_at").toLocalDateTime());
    }

    private String tagsToString(List<ReviewTag> tags) {
        if (tags == null || tags.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) builder.append(',');
            builder.append(tags.get(i).name());
        }
        return builder.toString();
    }

    private List<ReviewTag> tagsFromString(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<ReviewTag> tags = new ArrayList<>();
        for (String part : raw.split(",")) {
            try { tags.add(ReviewTag.valueOf(part.trim())); } catch (IllegalArgumentException ignored) { }
        }
        return tags;
    }
}
