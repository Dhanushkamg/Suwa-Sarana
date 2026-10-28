-- V25: one phone format everywhere (E.164, +94XXXXXXXXX) and no plaintext OTP codes at rest.
--
-- 1. users.phone_number: 07XXXXXXXX / 947XXXXXXXX / 0094... / 7XXXXXXXX  ->  +947XXXXXXXX.
--    A number whose normalised form is already owned by another account is left as stored
--    (skipped, logged as a WARNING) so the unique constraint is never violated; an operator
--    can resolve those rows by hand.
-- 2. otp_verifications: codes are now stored as an HMAC-SHA256 hex digest (64 chars). Pending codes
--    are short-lived (<= 40 minutes), so every existing row, which holds a plaintext code, is dropped.

DO $$
DECLARE
    r    RECORD;
    norm TEXT;
BEGIN
    FOR r IN SELECT id, phone_number FROM users ORDER BY id LOOP
        norm := regexp_replace(r.phone_number, '[^0-9+]', '', 'g');
        IF    norm ~ '^0[0-9]{9}$'    THEN norm := '+94' || substr(norm, 2);
        ELSIF norm ~ '^94[0-9]{9}$'   THEN norm := '+' || norm;
        ELSIF norm ~ '^0094[0-9]{9}$' THEN norm := '+' || substr(norm, 3);
        ELSIF norm ~ '^[0-9]{9}$'     THEN norm := '+94' || norm;
        END IF;

        IF norm ~ '^\+94[0-9]{9}$' AND norm <> r.phone_number THEN
            IF EXISTS (SELECT 1 FROM users WHERE phone_number = norm AND id <> r.id) THEN
                RAISE WARNING 'V25: phone number of user id % collides with another account after normalisation; left unchanged', r.id;
            ELSE
                UPDATE users SET phone_number = norm WHERE id = r.id;
            END IF;
        END IF;
    END LOOP;
END
$$;

DELETE FROM otp_verifications;
ALTER TABLE otp_verifications ALTER COLUMN otp_code TYPE VARCHAR(64);
