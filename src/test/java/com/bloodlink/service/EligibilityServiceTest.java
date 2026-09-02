package com.bloodlink.service;

import com.bloodlink.model.AvailabilityStatus;
import com.bloodlink.model.BloodGroup;
import com.bloodlink.model.Donor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class EligibilityServiceTest {
    private final EligibilityService service = new EligibilityService();

    @Test
    void eligibleAdultWithEnoughWeightAndNoRecentDonationPasses() {
        Donor donor = donor(LocalDate.now().minusYears(23), 65, LocalDate.now().minusDays(80));
        EligibilityService.EligibilityResult result = service.evaluate(donor);
        assertTrue(result.eligible());
        assertEquals(0, result.cooldownDaysRemaining());
    }

    @Test
    void recentDonationReportsExactRemainingCooldown() {
        Donor donor = donor(LocalDate.now().minusYears(24), 62, LocalDate.now().minusDays(20));
        EligibilityService.EligibilityResult result = service.evaluate(donor);
        assertFalse(result.eligible());
        assertEquals(36, result.cooldownDaysRemaining());
        assertTrue(result.reason().contains("36 cooldown day(s) remaining"));
    }

    @Test
    void underageAndUnderweightReasonsAreBothReturned() {
        Donor donor = donor(LocalDate.now().minusYears(17), 48, null);
        EligibilityService.EligibilityResult result = service.evaluate(donor);
        assertFalse(result.eligible());
        assertTrue(result.reason().contains("Age is below 18 years"));
        assertTrue(result.reason().contains("Weight is below 50 kg"));
    }

    private Donor donor(LocalDate birthDate, double weight, LocalDate lastDonationDate) {
        return new Donor(1, "Test Donor", "donor@test.local", "01700000000", "Dhaka", "",
                true, true, LocalDateTime.now(), BloodGroup.O_POSITIVE, birthDate, weight,
                lastDonationDate, AvailabilityStatus.AVAILABLE, 0, null);
    }
}
