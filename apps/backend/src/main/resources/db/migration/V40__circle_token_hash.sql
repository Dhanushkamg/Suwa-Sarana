-- V40: circle invite tokens are no longer stored in clear (F-38).
--   invite_token_hash : SHA-256 (hex) of the token; the only thing used to look a circle up
--   invite_token_enc  : AES-GCM ciphertext so the requester can fetch the same link again (filled by the application)
--   invite_token      : legacy plaintext, now nullable; the application empties it at start-up
--                       (CircleTokenMigrator) and a later migration can drop it once no environment has any left.

ALTER TABLE request_circles ADD COLUMN IF NOT EXISTS invite_token_hash VARCHAR(64);
ALTER TABLE request_circles ADD COLUMN IF NOT EXISTS invite_token_enc VARCHAR(512);

UPDATE request_circles
SET invite_token_hash = encode(sha256(convert_to(invite_token, 'UTF8')), 'hex')
WHERE invite_token_hash IS NULL AND invite_token IS NOT NULL;

ALTER TABLE request_circles ALTER COLUMN invite_token DROP NOT NULL;
ALTER TABLE request_circles ALTER COLUMN invite_token_hash SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_request_circles_token_hash ON request_circles (invite_token_hash);
