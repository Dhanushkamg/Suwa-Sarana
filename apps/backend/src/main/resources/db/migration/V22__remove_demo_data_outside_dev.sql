-- V22__remove_demo_data_outside_dev.sql                                                              (audit F-02 / F-13)
--
-- V3, V9 and V10 inserted demo users (admin@test.com is a VERIFIED ADMIN), their requests, matches, circles, camps,
-- registrations, reports and "blood bank" inventory into EVERY database, production included. This migration deletes
-- that data again, but only when Flyway's placeholder `environment` (env APP_ENV; prod unless the dev profile sets
-- it to 'dev') is not 'dev'. Local development databases keep the demo rows, which DataInitializer needs.
--
-- Safety rules - nothing outside the demo data is touched:
--   * every delete is keyed explicitly: the 8 demo e-mail addresses / phone numbers below, and the 8 demo inventory
--     (hospital_name, blood_type) pairs. No pattern such as LIKE '%@test.com' is used.
--   * rows are removed only through the demo users' ids: their blood requests (with matches, notification logs,
--     circles, circle pledges and reports hanging off them), their camps/slots, and bookings/matches of their donor
--     profiles. Requests, camps or slots owned by real users are never deleted.
--   * a demo user that real data still depends on (a real donor booked a slot/camp registered at a demo hospital)
--     is NOT deleted; it stays with its password disabled and is reported with a NOTICE for manual review.
--   * idempotent: running it on a database that has no demo rows deletes nothing.
--
-- To keep the demo data on purpose, set APP_ENV=dev (only sensible with the dev profile on a throw-away database).

DO $$
DECLARE
    demo_emails CONSTANT text[] := ARRAY[
        'admin@test.com', 'hospital@test.com', 'pending_hospital@test.com',
        'donor@test.com', 'donor2@test.com', 'donor3@test.com',
        'requester@test.com', 'unverified@test.com'];
    demo_phones CONSTANT text[] := ARRAY[
        '0777654321', '0771234567', '0779998877',
        '0773334455', '0774445566', '0775556677',
        '0772223344', '0761234567'];
    demo_user_ids    bigint[];
    demo_profile_ids bigint[];
    protected_ids    bigint[];
    removable_ids    bigint[];
    n bigint;
BEGIN
    IF '${environment}' = 'dev' THEN
        RAISE NOTICE 'V22: environment=dev, demo data is kept.';
        RETURN;
    END IF;

    SELECT coalesce(array_agg(id), '{}') INTO demo_user_ids FROM users WHERE email = ANY (demo_emails);
    SELECT coalesce(array_agg(id), '{}') INTO demo_profile_ids FROM donor_profiles WHERE user_id = ANY (demo_user_ids);

    -- Demo users that real (non-demo) donors still booked a slot or camp registration with.
    SELECT coalesce(array_agg(DISTINCT u.id), '{}') INTO protected_ids
    FROM unnest(demo_user_ids) AS u(id)
    WHERE EXISTS (
              SELECT 1
              FROM slot_bookings sb
              JOIN donation_slots s ON s.id = sb.slot_id
              LEFT JOIN donation_camps c ON c.id = s.camp_id
              WHERE (s.hospital_id = u.id OR c.organizer_id = u.id)
                AND NOT (sb.donor_id = ANY (demo_profile_ids)))
       OR EXISTS (
              SELECT 1
              FROM camp_registrations cr
              JOIN donation_camps c ON c.id = cr.camp_id
              WHERE c.organizer_id = u.id
                AND NOT (cr.donor_id = ANY (demo_profile_ids)));

    SELECT coalesce(array_agg(id), '{}') INTO removable_ids
    FROM unnest(demo_user_ids) AS u(id) WHERE NOT (u.id = ANY (protected_ids));

    -- 1. Requests of demo users (also of the demo users that stay: a request is never "real" data of a demo account).
    --    ON DELETE CASCADE removes their request_matches, notification_logs, request_circles (+ circle_responses, the
    --    fake pledgers "Kasun Perera" / "Nimal Fernando" and token circle-demo-colombo-2026) and request_reports.
    DELETE FROM blood_requests WHERE requester_id = ANY (demo_user_ids);
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'V22: removed % demo blood request(s)', n;

    -- 2. References to demo donor profiles that have no ON DELETE CASCADE (real requests the demo donors were matched to,
    --    bookings they made).
    DELETE FROM request_matches WHERE donor_id = ANY (demo_profile_ids);
    DELETE FROM slot_bookings   WHERE donor_id = ANY (demo_profile_ids);

    -- 3. The users. Cascades: donor_profiles (+ deferrals, notification logs, camp registrations), refresh_tokens,
    --    donation_camps (+ their slots, bookings, registrations), donation_slots hosted by the user, reports they filed.
    DELETE FROM users WHERE id = ANY (removable_ids);
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'V22: removed % demo user(s)', n;

    -- 4. Demo users that must stay: make sure nobody can log in as them (V20 already blanked the password) and still
    --    remove their seeded V10 camps (explicit names) unless a booking or a registration references them.
    UPDATE users SET password_hash = '!disabled-demo-account' WHERE id = ANY (protected_ids);
    DELETE FROM donation_camps c
    WHERE c.organizer_id = ANY (protected_ids)
      AND c.name IN ('Colombo Red Cross Blood Drive', 'Kandy Central Community Blood Camp', 'Southern Life Blood Donation Drive')
      AND NOT EXISTS (SELECT 1 FROM donation_slots s JOIN slot_bookings sb ON sb.slot_id = s.id WHERE s.camp_id = c.id)
      AND NOT EXISTS (SELECT 1 FROM camp_registrations cr WHERE cr.camp_id = c.id);
    IF array_length(protected_ids, 1) IS NOT NULL THEN
        RAISE NOTICE 'V22: kept % demo user(s) referenced by real bookings: ids %. Review and remove manually.',
            array_length(protected_ids, 1), protected_ids;
    END IF;

    -- 5. Pending OTP rows for the demo phone numbers (otherwise they could still be used to take over a re-created account).
    DELETE FROM otp_verifications WHERE phone_number = ANY (demo_phones);

    -- 6. Seeded "blood bank" inventory rows from V9 and V10 (explicit hospital/blood-type keys; the table has no write
    --    API, so rows with these exact keys are the seed).
    DELETE FROM blood_bank_inventory WHERE (hospital_name, blood_type) IN (
        ('National Hospital Colombo',                 'O-'),
        ('Teaching Hospital Kandy',                   'A-'),
        ('Karapitiya Teaching Hospital',              'B+'),
        ('Teaching Hospital Jaffna',                  'AB-'),
        ('Batticaloa General Hospital',               'O+'),
        ('Anuradhapura Teaching Hospital',            'A+'),
        ('Kurunegala Teaching Hospital',              'O-'),
        ('Badulla Provincial General Hospital',       'B-'));
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'V22: removed % seeded inventory row(s)', n;
END
$$;
