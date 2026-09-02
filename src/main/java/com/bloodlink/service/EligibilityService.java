package com.bloodlink.service;

import com.bloodlink.model.Donor;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public final class EligibilityService {
    public static final int MINIMUM_AGE = 18;
    public static final double MINIMUM_WEIGHT_KG = 50.0;
    public static final int COOLDOWN_DAYS = 56;

    public EligibilityResult evaluate(Donor donor) {
        List<String> reasons = new ArrayList<>();
        int age = donor.getBirthDate() == null ? 0 : Period.between(donor.getBirthDate(), LocalDate.now()).getYears();
        if (age < MINIMUM_AGE) reasons.add("Age is below 18 years");
        if (donor.getWeightKg() < MINIMUM_WEIGHT_KG) reasons.add("Weight is below 50 kg");
        long cooldownRemaining = cooldownDaysRemaining(donor);
        if (cooldownRemaining > 0) reasons.add(cooldownRemaining + " cooldown day(s) remaining");
        return reasons.isEmpty()
                ? new EligibilityResult(true, "Eligible to donate now", 0)
                : new EligibilityResult(false, String.join("; ", reasons), cooldownRemaining);
    }

    public long cooldownDaysRemaining(Donor donor) {
        if (donor.getLastDonationDate() == null) return 0;
        long elapsed = ChronoUnit.DAYS.between(donor.getLastDonationDate(), LocalDate.now());
        return Math.max(0, COOLDOWN_DAYS - elapsed);
    }

    public record EligibilityResult(boolean eligible, String reason, long cooldownDaysRemaining) { }
}
