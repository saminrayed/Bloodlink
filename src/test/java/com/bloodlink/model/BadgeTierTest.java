package com.bloodlink.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BadgeTierTest {
    @Test
    void mapsVerifiedDonationCountsToExpectedTier() {
        assertEquals(BadgeTier.NONE, BadgeTier.fromDonationCount(0));
        assertEquals(BadgeTier.BRONZE, BadgeTier.fromDonationCount(1));
        assertEquals(BadgeTier.SILVER, BadgeTier.fromDonationCount(3));
        assertEquals(BadgeTier.GOLD, BadgeTier.fromDonationCount(6));
        assertEquals(BadgeTier.PLATINUM, BadgeTier.fromDonationCount(10));
        assertEquals(BadgeTier.PLATINUM, BadgeTier.fromDonationCount(30));
    }
}
