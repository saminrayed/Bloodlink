package com.bloodlink.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record BloodRequest(
        long id,
        long requesterId,
        String requesterName,
        BloodGroup bloodGroup,
        int unitsNeeded,
        int unitsFulfilled,
        Urgency urgency,
        String hospitalName,
        Long hospitalId,
        Double hospitalLatitude,
        Double hospitalLongitude,
        String district,
        LocalDate deadline,
        String notes,
        RequestStatus status,
        Long acceptedDonorId,
        LocalDateTime donorConfirmedAt,
        LocalDateTime requesterConfirmedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /** True when this request is linked to a curated hospital record with known coordinates. */
    public boolean hasKnownHospitalLocation() {
        return hospitalLatitude != null && hospitalLongitude != null;
    }

    /**
     * @deprecated Left over from the single-donor model; still populated for the demo
     * data path but no longer authoritative. Under multi-donor, "who accepted" and
     * "who confirmed" live per-row on request_matches, since more than one donor can
     * be accepted at once. Use request_matches state (via findMatchesForRequest) instead.
     */
    @Deprecated
    public boolean donorHasConfirmed() { return donorConfirmedAt != null; }

    /** @deprecated see {@link #donorHasConfirmed()}. */
    @Deprecated
    public boolean requesterHasConfirmed() { return requesterConfirmedAt != null; }

    public int unitsRemaining() { return Math.max(0, unitsNeeded - unitsFulfilled); }

    public boolean isFullyFulfilled() { return unitsFulfilled >= unitsNeeded; }
}
