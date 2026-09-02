package com.bloodlink.model;

import java.time.LocalDate;

public record DonationRecord(long id, long donorId, Long requestId, LocalDate donationDate,
                             String hospitalName, BloodGroup bloodGroup, int units, boolean verified) { }
