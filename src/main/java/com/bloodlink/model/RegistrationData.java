package com.bloodlink.model;

import java.time.LocalDate;

public record RegistrationData(
        Role role,
        String fullName,
        String email,
        String phone,
        String district,
        String address,
        String password,
        BloodGroup bloodGroup,
        LocalDate birthDate,
        Double weightKg,
        LocalDate lastDonationDate
) { }
