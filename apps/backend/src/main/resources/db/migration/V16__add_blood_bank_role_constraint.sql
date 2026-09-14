-- V16: Drop the old role CHECK constraint and re-add it with BLOOD_BANK_REQUESTER included
-- This unblocks Blood Bank registration which was failing with "users_role_check" violation.

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;

ALTER TABLE users
    ADD CONSTRAINT users_role_check
    CHECK (role IN ('DONOR', 'REQUESTER', 'HOSPITAL_REQUESTER', 'BLOOD_BANK_REQUESTER', 'ADMIN'));
