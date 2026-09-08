-- Phase 1: Core Schema Initialization
-- Sri Lanka Personal Data Protection Act (2022) compliance notes:
-- 1. Right to deletion: ON DELETE CASCADE used for related donor profiles and deferrals.
-- 2. Data minimization: nic_number and phone_number must be protected in application layer APIs.

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) UNIQUE NOT NULL,
    phone_number    VARCHAR(20) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(30) NOT NULL CHECK (role IN ('DONOR','REQUESTER','HOSPITAL_REQUESTER','ADMIN')),
    nic_number      VARCHAR(20) UNIQUE,           
    is_verified     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE donor_profiles (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blood_type          VARCHAR(3) NOT NULL CHECK (blood_type IN ('A+','A-','B+','B-','AB+','AB-','O+','O-')),
    date_of_birth       DATE NOT NULL,
    weight_kg           NUMERIC(5,2),
    district            VARCHAR(50) NOT NULL,
    latitude            DOUBLE PRECISION NOT NULL,
    longitude           DOUBLE PRECISION NOT NULL,
    is_available        BOOLEAN NOT NULL DEFAULT TRUE,
    reliability_score   NUMERIC(4,2) NOT NULL DEFAULT 50.00,
    quiet_hours_start   TIME,
    quiet_hours_end     TIME
);

CREATE TABLE donor_deferrals (
    id             BIGSERIAL PRIMARY KEY,
    donor_id       BIGINT NOT NULL REFERENCES donor_profiles(id) ON DELETE CASCADE,
    reason         VARCHAR(100) NOT NULL,
    deferred_from  DATE NOT NULL,
    deferred_until DATE NOT NULL,
    notes          TEXT
);

CREATE TABLE blood_requests (
    id                  BIGSERIAL PRIMARY KEY,
    requester_id        BIGINT NOT NULL REFERENCES users(id),
    patient_blood_type  VARCHAR(3) NOT NULL,
    units_needed        SMALLINT NOT NULL DEFAULT 1,
    urgency             VARCHAR(20) NOT NULL CHECK (urgency IN ('ROUTINE','URGENT','CRITICAL')),
    hospital_name       VARCHAR(150) NOT NULL,
    district            VARCHAR(50) NOT NULL,
    latitude            DOUBLE PRECISION NOT NULL,
    longitude           DOUBLE PRECISION NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','ESCALATING','MATCHED','FULFILLED','CANCELLED','EXPIRED')),
    current_radius_km   SMALLINT NOT NULL DEFAULT 5,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ NOT NULL
);

CREATE TABLE request_matches (
    id            BIGSERIAL PRIMARY KEY,
    request_id    BIGINT NOT NULL REFERENCES blood_requests(id) ON DELETE CASCADE,
    donor_id      BIGINT NOT NULL REFERENCES donor_profiles(id),
    distance_km   NUMERIC(6,2) NOT NULL,
    notified_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    response      VARCHAR(20) CHECK (response IN ('PENDING','ACCEPTED','DECLINED','NO_RESPONSE')),
    responded_at  TIMESTAMPTZ,
    donated       BOOLEAN,
    UNIQUE(request_id, donor_id)
);

-- Extensions for geo queries
CREATE EXTENSION IF NOT EXISTS cube;
CREATE EXTENSION IF NOT EXISTS earthdistance;

-- Hot path indices
CREATE INDEX idx_donor_matching ON donor_profiles (blood_type, is_available, district);
CREATE INDEX idx_donor_geo ON donor_profiles USING gist (ll_to_earth(latitude, longitude));
CREATE INDEX idx_deferrals_donor_active ON donor_deferrals (donor_id, deferred_until);
CREATE INDEX idx_requests_status_district ON blood_requests (status, district);
CREATE INDEX idx_matches_request ON request_matches (request_id, response);
