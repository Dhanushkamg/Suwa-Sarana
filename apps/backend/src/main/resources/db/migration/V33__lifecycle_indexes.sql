-- V33: indexes for the request lifecycle, matching timeouts, analytics and camp listings (audit F-40).
CREATE INDEX IF NOT EXISTS idx_requests_requester      ON blood_requests (requester_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_requests_status_expires ON blood_requests (status, expires_at);
CREATE INDEX IF NOT EXISTS idx_requests_closed         ON blood_requests (status, closed_at);
CREATE INDEX IF NOT EXISTS idx_matches_donor           ON request_matches (donor_id);
CREATE INDEX IF NOT EXISTS idx_matches_pending_since   ON request_matches (notified_at) WHERE response = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_camps_status_date       ON donation_camps (status, scheduled_date);
CREATE INDEX IF NOT EXISTS idx_camps_organizer         ON donation_camps (organizer_id);
CREATE INDEX IF NOT EXISTS idx_donor_profiles_district ON donor_profiles (district);
