package com.bloodlink.model;

import java.time.LocalDate;

/**
 * @param distanceKm            Approximate km from this donor's location to the request's
 *                               hospital, or {@code null} when unknown.
 * @param requesterRating       The requester's average rating from verified-donation reviews,
 *                               or {@code null} if they have none yet.
 * @param requesterReviewCount  How many reviews {@code requesterRating} is based on.
 * @param donorConfirmed        Whether this donor has confirmed their side of the handshake.
 * @param requesterConfirmed    Whether the requester has confirmed their side, for this donor
 *                               specifically -- another donor on the same request may be in a
 *                               different state.
 */
public record DonorMatchView(long requestId, BloodGroup bloodGroup, String hospitalName, String district,
                             Urgency urgency, LocalDate deadline, RequestStatus requestStatus,
                             MatchStatus matchStatus, double score, Double distanceKm,
                             Double requesterRating, long requesterReviewCount,
                             int unitsNeeded, int unitsFulfilled,
                             boolean donorConfirmed, boolean requesterConfirmed) { }
