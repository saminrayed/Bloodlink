-- ============================================================================
-- Migration 004: Donor-chosen reference hospital
--
-- A donor may optionally pick the real hospital nearest to where they
-- actually are (reusing the same searchable picker used for requests) as a
-- precise stand-in for their location, instead of relying solely on the
-- coarser district-level default (first hospital seeded for their
-- district). Purely additive and optional: donors who never set this keep
-- getting the exact same behavior as before.
-- ============================================================================

ALTER TABLE donor_profiles
    ADD COLUMN reference_hospital_id BIGINT NULL AFTER verified_donation_count;

ALTER TABLE donor_profiles
    ADD CONSTRAINT fk_donor_reference_hospital FOREIGN KEY (reference_hospital_id)
        REFERENCES hospitals(id) ON DELETE SET NULL;

ALTER TABLE donor_profiles
    ADD INDEX idx_donor_reference_hospital (reference_hospital_id);
