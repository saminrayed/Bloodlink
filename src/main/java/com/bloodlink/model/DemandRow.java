package com.bloodlink.model;

public record DemandRow(BloodGroup bloodGroup, long pendingRequests, long availableDonors) { }
