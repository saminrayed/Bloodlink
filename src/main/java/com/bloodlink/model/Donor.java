package com.bloodlink.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class Donor extends User {
    private BloodGroup bloodGroup;
    private LocalDate birthDate;
    private double weightKg;
    private LocalDate lastDonationDate;
    private AvailabilityStatus availabilityStatus;
    private int verifiedDonationCount;
    private Long referenceHospitalId;

    public Donor(long id, String fullName, String email, String phone, String district, String address,
                 boolean approved, boolean active, LocalDateTime createdAt, BloodGroup bloodGroup,
                 LocalDate birthDate, double weightKg, LocalDate lastDonationDate,
                 AvailabilityStatus availabilityStatus, int verifiedDonationCount, Long referenceHospitalId) {
        super(id, fullName, email, phone, district, address, Role.DONOR, approved, active, createdAt);
        this.bloodGroup = bloodGroup;
        this.birthDate = birthDate;
        this.weightKg = weightKg;
        this.lastDonationDate = lastDonationDate;
        this.availabilityStatus = availabilityStatus;
        this.verifiedDonationCount = verifiedDonationCount;
        this.referenceHospitalId = referenceHospitalId;
    }

    public BloodGroup getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(BloodGroup bloodGroup) { this.bloodGroup = bloodGroup; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public double getWeightKg() { return weightKg; }
    public void setWeightKg(double weightKg) { this.weightKg = weightKg; }
    public LocalDate getLastDonationDate() { return lastDonationDate; }
    public void setLastDonationDate(LocalDate lastDonationDate) { this.lastDonationDate = lastDonationDate; }
    public AvailabilityStatus getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(AvailabilityStatus availabilityStatus) { this.availabilityStatus = availabilityStatus; }
    public int getVerifiedDonationCount() { return verifiedDonationCount; }
    public void setVerifiedDonationCount(int verifiedDonationCount) { this.verifiedDonationCount = verifiedDonationCount; }
    public BadgeTier getBadgeTier() { return BadgeTier.fromDonationCount(verifiedDonationCount); }

    /** The hospital this donor has chosen as closest to where they actually are, or null if they haven't set one. */
    public Long getReferenceHospitalId() { return referenceHospitalId; }
    public void setReferenceHospitalId(Long referenceHospitalId) { this.referenceHospitalId = referenceHospitalId; }
}
