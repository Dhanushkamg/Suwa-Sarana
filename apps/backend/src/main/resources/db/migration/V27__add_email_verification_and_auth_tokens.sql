-- V27: email verification and the single-use tokens behind it.
--
-- auth_tokens holds EMAIL_VERIFY, PASSWORD_RESET and ADMIN_INVITE tokens. Only the SHA-256 hash of the
-- token is stored; the raw value exists only in the email that was sent. A token is single-use (used_at)
-- and short-lived (expires_at).

ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMPTZ;
-- Google only issues ID tokens with a verified email, so those accounts are verified from day one.
UPDATE users SET email_verified_at = created_at WHERE auth_provider = 'GOOGLE' AND email_verified_at IS NULL;

CREATE TABLE IF NOT EXISTS auth_tokens (
    id          BIGSERIAL PRIMARY KEY,
    token_type  VARCHAR(30)  NOT NULL CHECK (token_type IN ('EMAIL_VERIFY', 'PASSWORD_RESET', 'ADMIN_INVITE')),
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    user_id     BIGINT       REFERENCES users(id) ON DELETE CASCADE,
    email       VARCHAR(255),
    created_by  BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_auth_tokens_user_type  ON auth_tokens (user_id, token_type);
CREATE INDEX IF NOT EXISTS idx_auth_tokens_email_type ON auth_tokens (email, token_type);
CREATE INDEX IF NOT EXISTS idx_auth_tokens_expires_at ON auth_tokens (expires_at);
