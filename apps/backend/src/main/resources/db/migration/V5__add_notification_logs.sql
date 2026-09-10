-- Phase 3: Notification Delivery Logs for Auditability

CREATE TABLE notification_logs (
    id             BIGSERIAL PRIMARY KEY,
    donor_id       BIGINT NOT NULL REFERENCES donor_profiles(id) ON DELETE CASCADE,
    request_id     BIGINT NOT NULL REFERENCES blood_requests(id) ON DELETE CASCADE,
    channel        VARCHAR(20) NOT NULL,
    status         VARCHAR(30) NOT NULL,
    recipient      VARCHAR(100),
    message_body   TEXT,
    sent_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_logs_donor ON notification_logs (donor_id);
CREATE INDEX idx_notification_logs_request ON notification_logs (request_id);
