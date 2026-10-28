-- V31: persistent notification inbox.
-- The server stores message KEYS + params (JSON text), never a translated sentence: the frontend localises.
CREATE TABLE user_notifications (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type         VARCHAR(40)  NOT NULL,
    title_key    VARCHAR(100) NOT NULL,
    body_key     VARCHAR(100) NOT NULL,
    params       TEXT         NOT NULL DEFAULT '{}',
    reference_id BIGINT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at      TIMESTAMPTZ
);

-- Keyset paging and Last-Event-ID replay both walk (user_id, id)
CREATE INDEX idx_user_notifications_user   ON user_notifications (user_id, id DESC);
CREATE INDEX idx_user_notifications_unread ON user_notifications (user_id) WHERE read_at IS NULL;
