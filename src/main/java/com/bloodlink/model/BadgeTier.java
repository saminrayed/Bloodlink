package com.bloodlink.model;

public enum BadgeTier {
    NONE(0), BRONZE(1), SILVER(3), GOLD(6), PLATINUM(10);
    private final int minimumDonations;
    BadgeTier(int minimumDonations) { this.minimumDonations = minimumDonations; }
    public int getMinimumDonations() { return minimumDonations; }
    public static BadgeTier fromDonationCount(int count) {
        if (count >= PLATINUM.minimumDonations) return PLATINUM;
        if (count >= GOLD.minimumDonations) return GOLD;
        if (count >= SILVER.minimumDonations) return SILVER;
        if (count >= BRONZE.minimumDonations) return BRONZE;
        return NONE;
    }
}
