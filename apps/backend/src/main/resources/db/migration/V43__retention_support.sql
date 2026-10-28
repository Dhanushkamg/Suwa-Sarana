-- V43: bookkeeping for the retention jobs (docs/RETENTION.md) and pledge caps (F-25, F-38).

-- guest pledges are scrubbed (name/phone/notes removed) a while after the request closed
ALTER TABLE circle_responses ADD COLUMN IF NOT EXISTS scrubbed_at TIMESTAMPTZ;
-- per-phone pledge caps look pledges up by phone
CREATE INDEX IF NOT EXISTS idx_circle_responses_phone ON circle_responses (responder_phone, responded_at);

-- purge of expired refresh tokens
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

-- scrub of old notification logs
CREATE INDEX IF NOT EXISTS idx_notification_logs_sent_at ON notification_logs (sent_at);
