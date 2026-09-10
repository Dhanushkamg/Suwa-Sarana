-- V4__add_verification_status.sql
-- Add verification_status column and migrate existing data

ALTER TABLE users ADD COLUMN verification_status VARCHAR(20) DEFAULT 'PENDING';

UPDATE users SET verification_status = 'VERIFIED' WHERE is_verified = true;
UPDATE users SET verification_status = 'PENDING' WHERE is_verified = false;

ALTER TABLE users ALTER COLUMN verification_status SET NOT NULL;
ALTER TABLE users DROP COLUMN is_verified;
