package com.bloodlink.model;

public record DashboardStats(long totalDonors, long pendingRequests, long activeRequests,
                             double fulfillmentRate) { }
