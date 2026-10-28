-- V26: account status (suspension), per-account lockout, access-token revocation and refresh-token reuse detection.

ALTER TABLE users ADD COLUMN IF NOT EXISTS status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN IF NOT EXISTS status_changed_at  TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_count INTEGER     NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until       TIMESTAMPTZ;
-- Bumped on password change/reset and sign-out-everywhere; access tokens carry it (claim "tv") and die when it moves.
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version      INTEGER     NOT NULL DEFAULT 0;
-- Seeded demo logins (V3/V10): password reset must never revive them.
ALTER TABLE users ADD COLUMN IF NOT EXISTS demo_account       BOOLEAN     NOT NULL DEFAULT FALSE;

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check;
ALTER TABLE users ADD CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'));

UPDATE users SET demo_account = TRUE
WHERE email IN (
    'admin@test.com', 'hospital@test.com', 'pending_hospital@test.com',
    'donor@test.com', 'donor2@test.com', 'donor3@test.com',
    'requester@test.com', 'unverified@test.com'
);

-- A rotated refresh token is kept (marked used) until it expires, so presenting it again is detectable.
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS used_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
