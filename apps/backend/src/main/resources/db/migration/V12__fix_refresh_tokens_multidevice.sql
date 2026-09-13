-- V12: Fix refresh_tokens table to support multiple sessions per user (multi-device).
--
-- The original schema had a UNIQUE constraint on user_id (enforcing OneToOne),
-- which caused session invalidation when a user logged in from a second device.
-- Changing to a regular index allows multiple refresh tokens per user.
--
-- The unique constraint on token_hash is preserved (each token must be unique).

-- Drop the unique constraint on user_id if it exists (was added by JPA as @OneToOne)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_refresh_tokens_user_id'
          AND conrelid = 'refresh_tokens'::regclass
    ) THEN
        ALTER TABLE refresh_tokens DROP CONSTRAINT uk_refresh_tokens_user_id;
    END IF;
END
$$;

-- Also handle the case where the constraint name varies (JPA-generated name)
DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    SELECT c.conname INTO constraint_name
    FROM pg_constraint c
    JOIN pg_class t ON c.conrelid = t.oid
    WHERE t.relname = 'refresh_tokens'
      AND c.contype = 'u'
      AND array_length(c.conkey, 1) = 1
      AND EXISTS (
          SELECT 1 FROM pg_attribute a
          WHERE a.attrelid = t.oid
            AND a.attnum = c.conkey[1]
            AND a.attname = 'user_id'
      );
    IF constraint_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE refresh_tokens DROP CONSTRAINT ' || quote_ident(constraint_name);
    END IF;
END
$$;

-- Add a non-unique index on user_id for efficient lookups by user
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);
