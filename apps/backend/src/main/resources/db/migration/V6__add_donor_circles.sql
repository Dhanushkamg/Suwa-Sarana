-- Phase 5: Replacement-Donor Circles

CREATE TABLE request_circles (
    id            BIGSERIAL PRIMARY KEY,
    request_id    BIGINT NOT NULL REFERENCES blood_requests(id) ON DELETE CASCADE,
    invite_token  VARCHAR(64) UNIQUE NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_request_circles_token ON request_circles (invite_token);
CREATE INDEX idx_request_circles_request ON request_circles (request_id);

CREATE TABLE circle_responses (
    id             BIGSERIAL PRIMARY KEY,
    circle_id      BIGINT NOT NULL REFERENCES request_circles(id) ON DELETE CASCADE,
    responder_name VARCHAR(150) NOT NULL,
    responder_phone VARCHAR(20) NOT NULL,
    blood_type     VARCHAR(3) NOT NULL,
    notes          TEXT,
    responded_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_circle_responses_circle ON circle_responses (circle_id);
