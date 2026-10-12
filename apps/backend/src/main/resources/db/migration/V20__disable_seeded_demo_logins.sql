-- V3 and V10 seeded demo accounts (admin@test.com, ...) with a publicly known password in EVERY environment.
-- Make those logins unusable. Local development re-enables them with SEED_DATA_ENABLED=true (see DataInitializer).
UPDATE users
SET password_hash = '!disabled-demo-account'
WHERE email IN (
    'admin@test.com', 'hospital@test.com', 'pending_hospital@test.com',
    'donor@test.com', 'donor2@test.com', 'donor3@test.com',
    'requester@test.com', 'unverified@test.com'
);
