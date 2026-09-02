-- ============================================================================
-- Migration 002: Hospital directory + real-coordinate distance matching
-- Safe to run once against the existing bloodlink_db. Does not drop or
-- rewrite any existing row; only adds a table and one nullable column.
-- ============================================================================

-- 1. Hospital directory. Coordinates are real, sourced (Wikipedia/Wikidata/
--    OpenStreetMap), not approximations. Distance-to-hospital is therefore
--    accurate; distance-from-donor is approximate (district-level) until a
--    precise donor location is captured -- see LocationService.java.
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

-- 2. Link requests to a curated hospital record. Nullable and additive:
--    hospital_name (free text) is kept so existing rows and any future
--    "my hospital isn't listed" fallback keep working without a match.
ALTER TABLE blood_requests
    ADD COLUMN hospital_id BIGINT NULL AFTER hospital_name;

ALTER TABLE blood_requests
    ADD CONSTRAINT fk_request_hospital FOREIGN KEY (hospital_id)
        REFERENCES hospitals(id) ON DELETE SET NULL;

ALTER TABLE blood_requests
    ADD INDEX idx_request_hospital (hospital_id);

-- 3. Seed data: 11 real hospitals across the 7 divisions most likely to be
--    used by district field values already in the app. The first four names
--    match this project's existing demo-data strings exactly, so step 4
--    below will backfill hospital_id on the demo requests automatically.
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

-- 4. Backfill: link any existing blood_requests rows (including demo data)
--    to the matching curated hospital, wherever the free-text name and
--    district line up exactly. Rows with no match are left untouched --
--    they simply show "distance unavailable" until re-selected from the
--    searchable list.
UPDATE blood_requests br
JOIN hospitals h ON h.name = br.hospital_name AND h.district = br.district
SET br.hospital_id = h.id
WHERE br.hospital_id IS NULL;
