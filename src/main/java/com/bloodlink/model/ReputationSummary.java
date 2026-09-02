package com.bloodlink.model;

/**
 * A user's aggregate reputation from verified-donation reviews only. A
 * reviewCount of 0 means genuinely no reviews yet -- callers must render
 * that as "no reviews yet", never as a 0-star rating.
 */
public record ReputationSummary(long userId, double averageRating, long reviewCount) {
    public static ReputationSummary none(long userId) { return new ReputationSummary(userId, 0, 0); }
    public boolean hasReviews() { return reviewCount > 0; }
}
