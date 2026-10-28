-- V32: real blood-bank inventory (replaces the read-only seeded blood_bank_inventory ticker table).
-- The legacy table from V9/V10 is left in place but is no longer read by the application.
-- Thresholds and shelf-life values are chosen by each institution / NBTS, never by the platform.

-- One row per institution that publishes stock: where it is and whether its status may appear on the public ticker.
CREATE TABLE inventory_sites (
    owner_user_id  BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    site_name      VARCHAR(150) NOT NULL,
    district       VARCHAR(50),               -- required before the site appears on the public ticker
    latitude       DOUBLE PRECISION,
    longitude      DOUBLE PRECISION,
    public_signals BOOLEAN      NOT NULL DEFAULT TRUE,
    confirmed_at   TIMESTAMPTZ,               -- last time stock was changed or confirmed: drives the 48 h staleness rule
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Stock is held as batches (one row per collection / delivery) so expiry can be tracked per batch.
CREATE TABLE inventory_units (
    id            BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blood_type    VARCHAR(3)  NOT NULL CHECK (blood_type IN ('A+','A-','B+','B-','AB+','AB-','O+','O-')),
    component     VARCHAR(20) NOT NULL CHECK (component IN ('WHOLE_BLOOD','RBC','PLATELETS','PLASMA')),
    units         INT         NOT NULL CHECK (units >= 0),
    collected_on  DATE        NOT NULL,
    expires_on    DATE        NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DEPLETED','EXPIRED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (expires_on >= collected_on)
);
CREATE INDEX idx_inventory_units_owner  ON inventory_units (owner_user_id, blood_type, component, status);
CREATE INDEX idx_inventory_units_expiry ON inventory_units (status, expires_on);

CREATE TABLE inventory_thresholds (
    id                   BIGSERIAL PRIMARY KEY,
    owner_user_id        BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blood_type           VARCHAR(3)  NOT NULL CHECK (blood_type IN ('A+','A-','B+','B-','AB+','AB-','O+','O-')),
    component            VARCHAR(20) NOT NULL DEFAULT 'WHOLE_BLOOD' CHECK (component IN ('WHOLE_BLOOD','RBC','PLATELETS','PLASMA')),
    low_threshold        INT         NOT NULL CHECK (low_threshold >= 0),
    critical_threshold   INT         NOT NULL CHECK (critical_threshold >= 0),
    last_notified_status VARCHAR(20),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (owner_user_id, blood_type, component),
    CHECK (low_threshold >= critical_threshold)
);

-- Append-only audit trail of every change in stock.
CREATE TABLE inventory_movements (
    id            BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    unit_id       BIGINT      REFERENCES inventory_units(id) ON DELETE SET NULL,
    blood_type    VARCHAR(3)  NOT NULL,
    component     VARCHAR(20) NOT NULL,
    delta         INT         NOT NULL,
    reason        VARCHAR(20) NOT NULL CHECK (reason IN ('COLLECTION','ISSUE','EXPIRY','ADJUSTMENT','DISCARD')),
    request_id    BIGINT      REFERENCES blood_requests(id) ON DELETE SET NULL,
    note          VARCHAR(255),
    created_by    BIGINT      REFERENCES users(id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_inventory_movements_owner ON inventory_movements (owner_user_id, created_at DESC);
