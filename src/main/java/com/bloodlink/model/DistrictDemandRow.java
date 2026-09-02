package com.bloodlink.model;

/** Same shape as {@link DemandRow} but grouped by district instead of blood group -- geographic demand. */
public record DistrictDemandRow(String district, long pendingRequests, long availableDonors) {
    public long gap() { return pendingRequests - availableDonors; }
}
