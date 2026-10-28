-- V41: each booking is reminded at most once per window (F-27). The scheduler claims a window with
-- UPDATE ... SET reminder_xx_sent_at = now() WHERE reminder_xx_sent_at IS NULL, so concurrent runs cannot double send.
ALTER TABLE slot_bookings ADD COLUMN IF NOT EXISTS reminder_24h_sent_at TIMESTAMPTZ;
ALTER TABLE slot_bookings ADD COLUMN IF NOT EXISTS reminder_2h_sent_at TIMESTAMPTZ;
