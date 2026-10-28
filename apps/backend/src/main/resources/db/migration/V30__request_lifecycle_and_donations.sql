-- V30: request lifecycle timestamps, donation history, abuse-report detail.
-- (Engineer C: request lifecycle, matching, donation history.)

ALTER TABLE blood_requests ADD COLUMN IF NOT EXISTS closed_at    TIMESTAMPTZ;
ALTER TABLE blood_requests ADD COLUMN IF NOT EXISTS matched_at   TIMESTAMPTZ;
ALTER TABLE blood_requests ADD COLUMN IF NOT EXISTS close_reason VARCHAR(30);

-- Requests that were already closed before this migration: best-effort close time (never in the future).
UPDATE blood_requests
   SET closed_at = LEAST(expires_at, now())
 WHERE closed_at IS NULL AND status IN ('FULFILLED', 'CANCELLED', 'EXPIRED');

-- Tidy district text so grouping works: trim, drop a trailing 'District', Title Case.
UPDATE blood_requests  SET district = initcap(trim(regexp_replace(district, '\s+district\s*$', '', 'i'))) WHERE district <> initcap(trim(regexp_replace(district, '\s+district\s*$', '', 'i')));
UPDATE donor_profiles  SET district = initcap(trim(regexp_replace(district, '\s+district\s*$', '', 'i'))) WHERE district <> initcap(trim(regexp_replace(district, '\s+district\s*$', '', 'i')));
UPDATE donation_camps  SET district = initcap(trim(regexp_replace(district, '\s+district\s*$', '', 'i'))) WHERE district <> initcap(trim(regexp_replace(district, '\s+district\s*$', '', 'i')));

-- One row per recorded donation. Written only by DonationRecorder (match confirmation or slot check-in).
CREATE TABLE donation_records (
    id           BIGSERIAL PRIMARY KEY,
    donor_id     BIGINT       NOT NULL REFERENCES donor_profiles(id) ON DELETE CASCADE,
    donated_on   DATE         NOT NULL,
    source       VARCHAR(20)  NOT NULL CHECK (source IN ('MATCH', 'SLOT_CHECKIN', 'MANUAL')),
    request_id   BIGINT       REFERENCES blood_requests(id)  ON DELETE SET NULL,
    match_id     BIGINT       REFERENCES request_matches(id) ON DELETE SET NULL,
    booking_id   BIGINT       REFERENCES slot_bookings(id)   ON DELETE SET NULL,
    host_user_id BIGINT       REFERENCES users(id)           ON DELETE SET NULL,
    host_name    VARCHAR(150),
    district     VARCHAR(50),
    blood_type   VARCHAR(3),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_donation_records_match   ON donation_records (match_id)   WHERE match_id   IS NOT NULL;
CREATE UNIQUE INDEX uq_donation_records_booking ON donation_records (booking_id) WHERE booking_id IS NOT NULL;
CREATE INDEX idx_donation_records_donor ON donation_records (donor_id, donated_on DESC);
CREATE INDEX idx_donation_records_host  ON donation_records (host_user_id, donated_on DESC);
CREATE INDEX idx_donation_records_req   ON donation_records (request_id);

-- Abuse reports: structured reason, optional capped details, at most one report per user per request.
ALTER TABLE request_reports ADD COLUMN IF NOT EXISTS reason_code VARCHAR(30);
ALTER TABLE request_reports ADD COLUMN IF NOT EXISTS details     VARCHAR(500);
CREATE UNIQUE INDEX IF NOT EXISTS uq_request_reports_once ON request_reports (request_id, reported_by);
