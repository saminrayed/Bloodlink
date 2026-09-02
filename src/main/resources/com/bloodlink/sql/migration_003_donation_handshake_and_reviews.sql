-- ============================================================================
-- Migration 003: Two-sided donation handshake + mutual reviews
-- Safe to run once against the existing bloodlink_db. Additive only.
-- ============================================================================

ALTER TABLE blood_requests
    ADD COLUMN donor_confirmed_at TIMESTAMP NULL AFTER accepted_donor_id,
    ADD COLUMN requester_confirmed_at TIMESTAMP NULL AFTER donor_confirmed_at;

-- Any request that is already sitting in FULFILLED from the old single-button
-- flow gets both timestamps backfilled to its existing updated_at, so it does
-- not appear to be "half confirmed" and so its donor/requester can review
-- each other retroactively (the review system only requires FULFILLED).
UPDATE blood_requests
SET donor_confirmed_at = updated_at, requester_confirmed_at = updated_at
WHERE status = 'FULFILLED' AND donor_confirmed_at IS NULL;

CREATE TABLE IF NOT EXISTS reviews (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    reviewed_id BIGINT NOT NULL,
    rating TINYINT NOT NULL,
    tags VARCHAR(300) NOT NULL DEFAULT '',
    comment VARCHAR(500) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_review_request FOREIGN KEY (request_id) REFERENCES blood_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_reviewed FOREIGN KEY (reviewed_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_review_once UNIQUE (request_id, reviewer_id),
    INDEX idx_review_reviewed (reviewed_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
