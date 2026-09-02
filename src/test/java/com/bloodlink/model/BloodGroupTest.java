package com.bloodlink.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BloodGroupTest {
    @Test
    void oNegativeCanDonateToEveryGroup() {
        for (BloodGroup recipient : BloodGroup.values()) {
            assertTrue(BloodGroup.O_NEGATIVE.canDonateTo(recipient));
        }
    }

    @Test
    void abPositiveCanDonateOnlyToAbPositive() {
        assertTrue(BloodGroup.AB_POSITIVE.canDonateTo(BloodGroup.AB_POSITIVE));
        assertEquals(1, BloodGroup.AB_POSITIVE.compatibleRecipients().size());
    }

    @Test
    void positiveBloodCannotDonateToNegativeRecipient() {
        assertFalse(BloodGroup.O_POSITIVE.canDonateTo(BloodGroup.O_NEGATIVE));
        assertFalse(BloodGroup.A_POSITIVE.canDonateTo(BloodGroup.A_NEGATIVE));
    }
}
