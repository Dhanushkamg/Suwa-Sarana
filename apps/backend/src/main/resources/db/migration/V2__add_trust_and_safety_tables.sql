-- Phase 1: Security and Trust Updates

-- 1. Refresh Tokens for Revocability
CREATE TABLE refresh_tokens (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash     VARCHAR(255) UNIQUE NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Index for fast token lookup
CREATE INDEX idx_refresh_token_hash ON refresh_tokens (token_hash);

-- 2. Request Reports for Abuse Prevention
CREATE TABLE request_reports (
    id             BIGSERIAL PRIMARY KEY,
    request_id     BIGINT NOT NULL REFERENCES blood_requests(id) ON DELETE CASCADE,
    reported_by    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason         TEXT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Index for admin queue queries
CREATE INDEX idx_request_reports_request ON request_reports (request_id);

-- 3. Gamification elements for Donor Profiles
ALTER TABLE donor_profiles
ADD COLUMN total_donations INTEGER NOT NULL DEFAULT 0,
ADD COLUMN lives_helped_estimate INTEGER NOT NULL DEFAULT 0;

-- 4. Escalation timestamp
ALTER TABLE blood_requests
ADD COLUMN last_escalated_at TIMESTAMPTZ NOT NULL DEFAULT now();
