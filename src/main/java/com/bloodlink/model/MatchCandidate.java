package com.bloodlink.model;

/**
 * @param distanceKm       Approximate km from the donor's district to the request's hospital,
 *                          or {@code null} when unknown. Never fabricated -- render {@code null}
 *                          as "distance unavailable".
 * @param averageRating    The donor's average rating from verified-donation reviews, or
 *                          {@code null} if they have none yet. Never render {@code null} as 0 stars.
 * @param reviewCount      How many reviews {@code averageRating} is based on.
 * @param matchStatus      NOTIFIED / ACCEPTED / DECLINED / EXPIRED for this specific donor's
 *                          match on this request -- several donors can independently be
 *                          ACCEPTED at once now that a request can need more than one donor.
 * @param donorConfirmed   Whether this donor has confirmed their side of the handshake.
 * @param requesterConfirmed Whether the requester has confirmed their side, for this donor.
 */
public record MatchCandidate(long donorId, String donorName, BloodGroup bloodGroup, String district,
                             String phone, double score, String reason, AvailabilityStatus availabilityStatus,
                             BadgeTier badgeTier, Double distanceKm, Double averageRating, long reviewCount,
                             MatchStatus matchStatus, boolean donorConfirmed, boolean requesterConfirmed) { }
