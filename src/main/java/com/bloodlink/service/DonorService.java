package com.bloodlink.service;

import com.bloodlink.dao.DonorDAO;
import com.bloodlink.model.AvailabilityStatus;

import java.sql.SQLException;
import java.time.LocalDate;

public final class DonorService {
    private final DonorDAO donorDAO = new DonorDAO();
    private final AuthorizationService authorizationService = new AuthorizationService();

    public ServiceResult<Void> updateAvailability(long donorId, AvailabilityStatus status) {
        if (status == null) return ServiceResult.failure("Choose an availability status.");
        try {
            authorizationService.requireSelfOrAdmin(donorId);
            donorDAO.updateAvailability(donorId, status);
            return ServiceResult.success("Availability changed to " + status + ".", null);
        } catch (SQLException e) {
            return ServiceResult.failure("Availability could not be updated: " + e.getMessage());
        }
    }

    public ServiceResult<Void> updateHealth(long donorId, String weightText, LocalDate lastDonationDate) {
        try {
            double weight = Double.parseDouble(weightText.trim());
            if (weight < 35 || weight > 250) return ServiceResult.failure("Weight must be between 35 and 250 kg.");
            if (lastDonationDate != null && lastDonationDate.isAfter(LocalDate.now()))
                return ServiceResult.failure("Last donation date cannot be in the future.");
            authorizationService.requireSelfOrAdmin(donorId);
            donorDAO.updateHealthProfile(donorId, weight, lastDonationDate);
            return ServiceResult.success("Health and cooldown information updated.", null);
        } catch (NumberFormatException e) {
            return ServiceResult.failure("Enter a valid numeric weight.");
        } catch (SQLException e) {
            return ServiceResult.failure("Health profile could not be updated: " + e.getMessage());
        }
    }

    /**
     * Sets the hospital a donor considers closest to where they actually are, used as a
     * precise stand-in for their location in distance-aware matching (see
     * LocationService). Pass null to clear it and fall back to the district default.
     */
    public ServiceResult<Void> updateReferenceHospital(long donorId, Long hospitalId) {
        try {
            authorizationService.requireSelfOrAdmin(donorId);
            donorDAO.updateReferenceHospital(donorId, hospitalId);
            return ServiceResult.success(hospitalId == null
                    ? "Reference hospital cleared. Distance will use your district instead."
                    : "Reference hospital saved. Your distance in matches will now be measured from there.", null);
        } catch (SQLException e) {
            return ServiceResult.failure("Reference hospital could not be updated: " + e.getMessage());
        }
    }
}
