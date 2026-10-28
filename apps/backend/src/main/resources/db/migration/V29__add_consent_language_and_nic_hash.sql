-- V29: PDPA groundwork: recorded consent, preferred language, erasure marker and a keyed NIC hash.
--
-- The NIC is never used for anything except duplicate detection, so it is no longer stored in clear:
-- nic_hash = HMAC-SHA256(server secret, normalised NIC). Existing plaintext values are hashed and nulled by
-- NicHashBackfill on the first start with the secret configured (a SQL migration cannot know the key).

ALTER TABLE users ADD COLUMN IF NOT EXISTS consent_version     VARCHAR(30);
ALTER TABLE users ADD COLUMN IF NOT EXISTS consented_at        TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS preferred_language  VARCHAR(2) NOT NULL DEFAULT 'en';
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at          TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS nic_hash            VARCHAR(64);

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_preferred_language_check;
ALTER TABLE users ADD CONSTRAINT users_preferred_language_check CHECK (preferred_language IN ('en', 'si', 'ta'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_nic_hash ON users (nic_hash) WHERE nic_hash IS NOT NULL;
