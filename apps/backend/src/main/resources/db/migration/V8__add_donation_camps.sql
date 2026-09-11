CREATE TABLE donation_camps (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    district       VARCHAR(50) NOT NULL,
    location       VARCHAR(255) NOT NULL,
    latitude       DOUBLE PRECISION,
    longitude      DOUBLE PRECISION,
    scheduled_date DATE NOT NULL,
    start_time     TIME NOT NULL,
    end_time       TIME NOT NULL,
    organizer_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status         VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE camp_registrations (
    id             BIGSERIAL PRIMARY KEY,
    camp_id        BIGINT NOT NULL REFERENCES donation_camps(id) ON DELETE CASCADE,
    donor_id       BIGINT NOT NULL REFERENCES donor_profiles(id) ON DELETE CASCADE,
    registered_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    attended       BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE(camp_id, donor_id)
);
