-- V23__add_indexes.sql                                                                                    (audit F-40)
--
-- Foreign keys and frequent filter columns that had no index (PostgreSQL does not index referencing columns
-- automatically). All statements are IF NOT EXISTS, so re-running on a database that already has an equivalent
-- index created by hand is harmless. The tables are small at launch; if one grows large, create the same indexes
-- with CREATE INDEX CONCURRENTLY outside Flyway before deploying this migration.

-- donor_profiles: every request resolves the profile of the logged-in user (DonorRepository.findByUserId).
CREATE INDEX IF NOT EXISTS idx_donor_profiles_user_id ON donor_profiles (user_id);

-- blood_requests: "my requests" (requester_id), admin/triage lists ordered by newest, retention and escalation scans.
CREATE INDEX IF NOT EXISTS idx_requests_requester ON blood_requests (requester_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_requests_created_at ON blood_requests (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_requests_status_expires ON blood_requests (status, expires_at);

-- request_matches: "my matches" for a donor (the unique key starts with request_id, so it cannot serve donor lookups).
CREATE INDEX IF NOT EXISTS idx_matches_donor ON request_matches (donor_id, notified_at DESC);

-- donation_camps: organiser's own camps, public listing by status + date.
CREATE INDEX IF NOT EXISTS idx_camps_organizer ON donation_camps (organizer_id, scheduled_date DESC);
CREATE INDEX IF NOT EXISTS idx_camps_status_date ON donation_camps (status, scheduled_date);

-- camp_registrations: lookups per donor (the unique key starts with camp_id).
CREATE INDEX IF NOT EXISTS idx_camp_registrations_donor ON camp_registrations (donor_id);

-- users: admin verification queue.
CREATE INDEX IF NOT EXISTS idx_users_verification_status ON users (verification_status);

-- refresh_tokens: purge of expired tokens.
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

-- request_reports: reports filed by a user (FK; cascades on user delete).
CREATE INDEX IF NOT EXISTS idx_request_reports_reported_by ON request_reports (reported_by);

-- otp_verifications: latest code per phone (findTopByPhoneNumberOrderByCreatedAtDesc) and the retention purge by age.
CREATE INDEX IF NOT EXISTS idx_otp_phone_created ON otp_verifications (phone_number, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_otp_created_at ON otp_verifications (created_at);

-- notification_logs: retention purge by age.
CREATE INDEX IF NOT EXISTS idx_notification_logs_sent_at ON notification_logs (sent_at);
