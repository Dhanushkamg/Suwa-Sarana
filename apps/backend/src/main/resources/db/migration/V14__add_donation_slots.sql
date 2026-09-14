-- V14__add_donation_slots.sql
-- Adds time-slotted booking sub-system for both Donation Camps and Hospital walk-in windows.
-- hospital_id references users(id) where role = 'HOSPITAL_REQUESTER' (no dedicated hospitals table).
-- Two nullable FKs + CHECK constraint give full referential integrity without a polymorphic host_id anti-pattern.

CREATE TABLE donation_slots (
    id            BIGSERIAL    PRIMARY KEY,
    camp_id       BIGINT       REFERENCES donation_camps(id) ON DELETE CASCADE,
    hospital_id   BIGINT       REFERENCES users(id)          ON DELETE CASCADE,
    CONSTRAINT chk_slot_host CHECK (
        (camp_id IS NOT NULL AND hospital_id IS NULL) OR
        (camp_id IS NULL     AND hospital_id IS NOT NULL)
    ),
    start_time    TIMESTAMP    NOT NULL,
    end_time      TIMESTAMP    NOT NULL,
    CONSTRAINT chk_slot_times CHECK (end_time > start_time),
    capacity      INT          NOT NULL CHECK (capacity > 0),
    booked_count  INT          NOT NULL DEFAULT 0 CHECK (booked_count >= 0),
    status        VARCHAR(20)  NOT NULL DEFAULT 'OPEN'
                               CHECK (status IN ('OPEN', 'FULL', 'CANCELLED', 'COMPLETED')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Partial indexes for efficient per-host slot lookups
CREATE INDEX idx_slots_camp     ON donation_slots (camp_id,     start_time) WHERE camp_id     IS NOT NULL;
CREATE INDEX idx_slots_hospital ON donation_slots (hospital_id, start_time) WHERE hospital_id IS NOT NULL;
-- Index for the reminder scheduler querying upcoming slot windows
CREATE INDEX idx_slots_start_time ON donation_slots (start_time, status);

CREATE TABLE slot_bookings (
    id             BIGSERIAL    PRIMARY KEY,
    slot_id        BIGINT       NOT NULL REFERENCES donation_slots(id) ON DELETE CASCADE,
    donor_id       BIGINT       NOT NULL REFERENCES donor_profiles(id),
    status         VARCHAR(20)  NOT NULL DEFAULT 'BOOKED'
                                CHECK (status IN ('BOOKED', 'CHECKED_IN', 'NO_SHOW', 'CANCELLED', 'COMPLETED')),
    booked_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    checked_in_at  TIMESTAMPTZ,
    eligible_as_of DATE,
    UNIQUE (slot_id, donor_id)
);

CREATE INDEX idx_bookings_donor ON slot_bookings (donor_id, booked_at DESC);
CREATE INDEX idx_bookings_slot  ON slot_bookings (slot_id, status);
