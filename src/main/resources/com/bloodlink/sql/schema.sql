CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    district VARCHAR(80) NOT NULL,
    address VARCHAR(255) NOT NULL DEFAULT '',
    photo MEDIUMBLOB NULL,
    role ENUM('DONOR','REQUESTER','ADMIN') NOT NULL,
    approved BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_role_state (role, approved, active),
    INDEX idx_users_district (district)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS donor_profiles (
    user_id BIGINT PRIMARY KEY,
    blood_group ENUM('O_NEGATIVE','O_POSITIVE','A_NEGATIVE','A_POSITIVE','B_NEGATIVE','B_POSITIVE','AB_NEGATIVE','AB_POSITIVE') NOT NULL,
    birth_date DATE NOT NULL,
    weight_kg DECIMAL(5,2) NOT NULL,
    last_donation_date DATE NULL,
    availability_status ENUM('AVAILABLE','BUSY','OUT_OF_TOWN','MEDICAL_HOLD') NOT NULL DEFAULT 'BUSY',
    verified_donation_count INT NOT NULL DEFAULT 0,
    reference_hospital_id BIGINT NULL,
    CONSTRAINT chk_donor_weight CHECK (weight_kg BETWEEN 35 AND 250),
    CONSTRAINT chk_verified_donations CHECK (verified_donation_count >= 0),
    CONSTRAINT fk_donor_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_donor_matching (availability_status, blood_group, last_donation_date),
    INDEX idx_donor_donation_count (verified_donation_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Curated hospital directory with real coordinates, used for the searchable
-- hospital picker and for distance-aware donor matching. Must be created
-- before blood_requests, which references it.
CREATE TABLE IF NOT EXISTS hospitals (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(180) NOT NULL,
    district VARCHAR(80) NOT NULL,
    area VARCHAR(120) NOT NULL DEFAULT '',
    address VARCHAR(255) NOT NULL DEFAULT '',
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    phone VARCHAR(30) NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_hospital_name_district (name, district),
    INDEX idx_hospital_district (district, active),
    INDEX idx_hospital_search (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Deferred until here because hospitals must exist first: a donor may
-- optionally choose the hospital nearest to where they actually are as a
-- precise stand-in for their location, instead of the coarser
-- district-level default (see LocationService.java).
ALTER TABLE donor_profiles
    ADD CONSTRAINT fk_donor_reference_hospital FOREIGN KEY (reference_hospital_id)
        REFERENCES hospitals(id) ON DELETE SET NULL,
    ADD INDEX idx_donor_reference_hospital (reference_hospital_id);

CREATE TABLE IF NOT EXISTS blood_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    requester_id BIGINT NOT NULL,
    blood_group ENUM('O_NEGATIVE','O_POSITIVE','A_NEGATIVE','A_POSITIVE','B_NEGATIVE','B_POSITIVE','AB_NEGATIVE','AB_POSITIVE') NOT NULL,
    units_needed INT NOT NULL,
    units_fulfilled INT NOT NULL DEFAULT 0,
    urgency ENUM('NORMAL','URGENT','CRITICAL') NOT NULL,
    hospital_name VARCHAR(180) NOT NULL,
    hospital_id BIGINT NULL,
    district VARCHAR(80) NOT NULL,
    deadline DATE NOT NULL,
    notes TEXT NOT NULL,
    status ENUM('PENDING','MATCHED','ACCEPTED','PARTIALLY_FULFILLED','DECLINED','FULFILLED','CANCELLED','ESCALATED') NOT NULL DEFAULT 'PENDING',
    accepted_donor_id BIGINT NULL,
    donor_confirmed_at TIMESTAMP NULL,
    requester_confirmed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_request_units CHECK (units_needed BETWEEN 1 AND 20),
    CONSTRAINT fk_request_requester FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_request_donor FOREIGN KEY (accepted_donor_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_request_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE SET NULL,
    INDEX idx_request_queue (status, urgency, deadline),
    INDEX idx_request_group_district (blood_group, district),
    INDEX idx_request_requester (requester_id, created_at),
    INDEX idx_request_hospital (hospital_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS request_matches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    donor_id BIGINT NOT NULL,
    match_score DECIMAL(6,2) NOT NULL,
    match_reason VARCHAR(500) NOT NULL,
    status ENUM('NOTIFIED','ACCEPTED','DECLINED','EXPIRED') NOT NULL DEFAULT 'NOTIFIED',
    matched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP NULL,
    donor_confirmed_at TIMESTAMP NULL,
    requester_confirmed_at TIMESTAMP NULL,
    CONSTRAINT fk_match_request FOREIGN KEY (request_id) REFERENCES blood_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_match_donor FOREIGN KEY (donor_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_request_donor UNIQUE (request_id, donor_id),
    INDEX idx_match_donor_state (donor_id, status, matched_at),
    INDEX idx_match_request_score (request_id, match_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS request_status_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    from_status ENUM('PENDING','MATCHED','ACCEPTED','PARTIALLY_FULFILLED','DECLINED','FULFILLED','CANCELLED','ESCALATED') NULL,
    to_status ENUM('PENDING','MATCHED','ACCEPTED','PARTIALLY_FULFILLED','DECLINED','FULFILLED','CANCELLED','ESCALATED') NOT NULL,
    changed_by BIGINT NULL,
    note VARCHAR(500) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_request FOREIGN KEY (request_id) REFERENCES blood_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_history_actor FOREIGN KEY (changed_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_history_request_time (request_id, changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(140) NOT NULL,
    message VARCHAR(700) NOT NULL,
    type VARCHAR(40) NOT NULL,
    related_request_id BIGINT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_request FOREIGN KEY (related_request_id) REFERENCES blood_requests(id) ON DELETE CASCADE,
    INDEX idx_notification_inbox (user_id, is_read, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS donation_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    donor_id BIGINT NOT NULL,
    request_id BIGINT NULL UNIQUE,
    donation_date DATE NOT NULL,
    hospital_name VARCHAR(180) NOT NULL,
    blood_group ENUM('O_NEGATIVE','O_POSITIVE','A_NEGATIVE','A_POSITIVE','B_NEGATIVE','B_POSITIVE','AB_NEGATIVE','AB_POSITIVE') NOT NULL,
    units INT NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_donation_units CHECK (units BETWEEN 1 AND 20),
    CONSTRAINT fk_donation_donor FOREIGN KEY (donor_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_donation_request FOREIGN KEY (request_id) REFERENCES blood_requests(id) ON DELETE SET NULL,
    INDEX idx_donation_donor_date (donor_id, donation_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_user_id BIGINT NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id BIGINT NULL,
    details VARCHAR(700) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_audit_time (created_at),
    INDEX idx_audit_entity (entity_type, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A review may only exist against a request that reached FULFILLED (both
-- donor and requester confirmed the donation happened) and only once per
-- reviewer per request -- enforced in ReviewService, backstopped here by
-- the unique key.
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci; Real, sourced coordinates -- see
-- migration_002_hospitals_and_distance.sql for citations. The first four
-- names match this project's existing demo blood_requests data exactly.
-- INSERT IGNORE + the unique key above keep this block safe to run more
-- than once (schema.sql is not otherwise guarded against re-execution).
INSERT IGNORE INTO hospitals (name, district, area, address, latitude, longitude, phone, active) VALUES
('Dhaka Medical College Hospital', 'Dhaka', 'Shahbagh', 'Secretariat Road, Shahbagh, Dhaka', 23.7257000, 90.3971000, '', TRUE),
('Square Hospital', 'Dhaka', 'West Panthapath', '18/F Bir Uttam Qazi Nuruzzaman Sarak, West Panthapath, Dhaka 1205', 23.7519000, 90.3855000, '', TRUE),
('United Hospital', 'Dhaka', 'Gulshan-2', 'Plot 15, Road 71, Gulshan-2, Dhaka', 23.8046000, 90.4158000, '', TRUE),
('Evercare Hospital Dhaka', 'Dhaka', 'Bashundhara R/A', 'Plot 81, Block E, Bashundhara R/A, Dhaka', 23.8108000, 90.4319000, '+880 9666-710678', TRUE),
('Shaheed Suhrawardy Medical College Hospital', 'Dhaka', 'Sher-e-Bangla Nagar', 'Sher-e-Bangla Nagar, Dhaka', 23.7685000, 90.3717000, '', TRUE),
('Chittagong Medical College Hospital', 'Chittagong', 'Chandanpura', 'K.B. Fazlul Kader Road, Chittagong', 22.3593000, 91.8307000, '', TRUE),
('Sylhet MAG Osmani Medical College Hospital', 'Sylhet', 'Sylhet Sadar', 'Sylhet Sadar, Sylhet', 24.9022000, 91.8535000, '', TRUE),
('Rajshahi Medical College Hospital', 'Rajshahi', 'Rajshahi Sadar', 'Laxmipur, Rajshahi', 24.3723000, 88.5857000, '', TRUE),
('Khulna Medical College Hospital', 'Khulna', 'Choto Boyra', 'Choto Boyra, Khulna', 22.8291000, 89.5370000, '', TRUE),
('Rangpur Medical College Hospital', 'Rangpur', 'Dhap', 'Dhap, Rangpur', 25.7667000, 89.2342000, '', TRUE),
('Sher-e-Bangla Medical College Hospital', 'Barisal', 'Barisal Sadar', 'Kirtonkhola River Bank, Barisal', 22.6880000, 90.3610000, '', TRUE),
('Mymensingh Medical College Hospital', 'Mymensingh', 'Charpara', 'Medical College Road, Charpara, Mymensingh', 24.7416000, 90.4093000, '', TRUE),
('Cumilla Medical College Hospital', 'Cumilla', 'Kuchaitoli', 'Dr Akhtar Hameed Khan Road, Kuchaitoli, Cumilla', 23.4511000, 91.2022000, '081-65401', TRUE),
('Shaheed Ziaur Rahman Medical College Hospital', 'Bogura', 'Silimpur', 'Bogura City Bypass, Silimpur, Bogura', 24.8280000, 89.3531000, '', TRUE),
('Shaheed M. Monsur Ali Medical College Hospital', 'Sirajganj', 'Sirajganj Sadar', 'Sirajganj Sadar, Sirajganj', 24.4488000, 89.6738000, '', TRUE),
('Dinajpur Medical College Hospital', 'Dinajpur', 'Dinajpur Sadar', 'Dinajpur Sadar, Dinajpur', 25.6106000, 88.6551000, '', TRUE),
('Cox''s Bazar Medical College Hospital', 'Cox''s Bazar', 'Jhilongja', 'Jhilongja, Cox''s Bazar', 21.4206000, 92.0149000, '', TRUE),
('Jashore Medical College Hospital', 'Jashore', 'Chanchra', 'Horinar Beel, Chanchra, Jashore', 23.1690000, 89.2090000, '', TRUE),
('Pabna General Hospital', 'Pabna', 'Hemayetpur', 'Hemayetpur, Pabna', 24.0046000, 89.2090000, '', TRUE),
('Noakhali Medical College Hospital', 'Noakhali', 'Chowmuhoni', 'Begumganj, Chowmuhoni, Noakhali', 22.9510000, 91.1040000, '', TRUE),
('Kushtia Medical College Hospital', 'Kushtia', 'Kushtia Sadar', 'Kushtia-Dhaka Highway, Kushtia', 23.9009000, 89.1233000, '', TRUE),
('Kurmitola General Hospital', 'Dhaka', 'Kurmitola', 'Kurmitola, Dhaka-1206', 23.8194400, 90.4092900, '', TRUE),
('Shaheed Ahsan Ullah Master General Hospital', 'Gazipur', 'Tongi', 'Station Road, Tongi, Gazipur', 23.8934000, 90.4022000, '', TRUE),
('Narayanganj 300 Bed Hospital', 'Narayanganj', 'Khanpur', 'Khanpur, Narayanganj Sadar, Narayanganj', 23.6264000, 90.5062000, '', TRUE);
