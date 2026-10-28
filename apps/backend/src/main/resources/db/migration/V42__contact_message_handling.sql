-- V42: admin inbox support for contact messages (F-21) and retention bookkeeping (F-25).
ALTER TABLE contact_messages ADD COLUMN IF NOT EXISTS handled_at TIMESTAMPTZ;
ALTER TABLE contact_messages ADD COLUMN IF NOT EXISTS handled_by VARCHAR(100);

UPDATE contact_messages SET status = 'NEW' WHERE status IS NULL;
-- rows that were (manually) marked handled before this column existed: the age of the message is the best proxy
UPDATE contact_messages SET handled_at = created_at WHERE status = 'HANDLED' AND handled_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_contact_messages_status_created ON contact_messages (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_contact_messages_handled_at ON contact_messages (handled_at) WHERE handled_at IS NOT NULL;
