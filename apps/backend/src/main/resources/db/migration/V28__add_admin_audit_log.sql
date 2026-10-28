-- V28: persisted audit trail of privileged actions (verification decisions, suspension, role changes, data exports)
-- and the latest verification decision on the user row.
--
-- No foreign keys on purpose: the trail must survive the deletion/anonymisation of the people it mentions.
-- Rows are append-only (UPDATE is rejected by a trigger). DELETE stays possible so a retention job can purge old rows.

CREATE TABLE IF NOT EXISTS admin_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    actor_id        BIGINT,
    actor_role      VARCHAR(30),
    action          VARCHAR(40)  NOT NULL,
    target_user_id  BIGINT,
    reason          VARCHAR(500),
    details         VARCHAR(1000),
    ip_address      VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_target ON admin_audit_log (target_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_audit_actor  ON admin_audit_log (actor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_audit_action ON admin_audit_log (action, created_at DESC);

CREATE OR REPLACE FUNCTION admin_audit_log_no_update() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'admin_audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_admin_audit_log_no_update ON admin_audit_log;
CREATE TRIGGER trg_admin_audit_log_no_update
    BEFORE UPDATE ON admin_audit_log
    FOR EACH ROW EXECUTE FUNCTION admin_audit_log_no_update();

ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_reviewed_by BIGINT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_reviewed_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_reason      VARCHAR(500);
