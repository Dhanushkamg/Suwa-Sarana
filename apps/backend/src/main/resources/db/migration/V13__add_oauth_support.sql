-- Add OAuth support columns to users table
ALTER TABLE users ADD COLUMN auth_provider VARCHAR(20) DEFAULT 'LOCAL' NOT NULL;
ALTER TABLE users ADD COLUMN provider_id VARCHAR(255);

-- Allow users signing up via OAuth to not have a local password
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
