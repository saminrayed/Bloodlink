-- ============================================================================
-- Migration 005: Multi-donor partial fulfillment -- SCHEMA GROUNDWORK ONLY
--
-- This migration is safe to run now, but the DAO/service/controller layers
-- have NOT been updated to use it yet -- that lands in a later delivery.
-- Until then:
--   - units_fulfilled will sit at 0 (or backfilled for already-FULFILLED
--     requests below) and nothing increments it further.
--   - request_matches.donor_confirmed_at/requester_confirmed_at exist but
--     are not yet written to; the existing single-donor handshake on
--     blood_requests keeps working exactly as it does today.
--   - No code path ever sets status='PARTIALLY_FULFILLED' yet.
-- Running this early just means the next delivery won't need its own
-- migration on top of whatever data has accumulated by then.
-- ============================================================================

-- How many of units_needed have actually been confirmed by both sides so far.
ALTER TABLE blood_requests
    ADD COLUMN units_fulfilled INT NOT NULL DEFAULT 0 AFTER units_needed;

-- Adding a new status is safe: existing rows keep whatever status they already have.
ALTER TABLE blood_requests
    MODIFY COLUMN status ENUM('PENDING','MATCHED','ACCEPTED','PARTIALLY_FULFILLED','DECLINED','FULFILLED','CANCELLED','ESCALATED')
        NOT NULL DEFAULT 'PENDING';

-- Backfill: requests already FULFILLED under the old single-donor model get
-- units_fulfilled = units_needed, so the new column agrees with reality.
UPDATE blood_requests SET units_fulfilled = units_needed WHERE status = 'FULFILLED' AND units_fulfilled = 0;

-- Per-donor handshake state. In the current single-donor model, only one
-- request_matches row per request is ever ACCEPTED at a time, so this
-- mirrors blood_requests.donor_confirmed_at/requester_confirmed_at exactly.
-- Once multiple donors can be simultaneously ACCEPTED (the actual multi-donor
-- change, not part of this migration), each needs its own handshake state,
-- which is why this lives on request_matches, not blood_requests.
ALTER TABLE request_matches
    ADD COLUMN donor_confirmed_at TIMESTAMP NULL AFTER responded_at,
    ADD COLUMN requester_confirmed_at TIMESTAMP NULL AFTER donor_confirmed_at;

-- Backfill: copy the existing per-request handshake state onto whichever
-- match row is the currently accepted donor for that request.
UPDATE request_matches rm
JOIN blood_requests br ON br.id = rm.request_id AND br.accepted_donor_id = rm.donor_id
SET rm.donor_confirmed_at = br.donor_confirmed_at,
    rm.requester_confirmed_at = br.requester_confirmed_at
WHERE rm.status = 'ACCEPTED';

-- NOT dropped yet, deliberately: blood_requests.accepted_donor_id,
-- donor_confirmed_at, and requester_confirmed_at are still read and written
-- by the current single-donor RequestDAO code. They become redundant once
-- the multi-donor DAO/service rewrite lands (request_matches becomes the
-- sole source of truth for "who accepted and confirmed"), and should be
-- dropped in a follow-up migration at that point, not before -- dropping
-- them now would break the app that is still running against them.
