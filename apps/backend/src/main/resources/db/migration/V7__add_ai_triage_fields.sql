-- V7__add_ai_triage_fields.sql
-- Add fraud risk scoring and AI triage diagnostic hint columns to blood_requests

ALTER TABLE blood_requests ADD COLUMN IF NOT EXISTS fraud_risk_score INTEGER DEFAULT 0;
ALTER TABLE blood_requests ADD COLUMN IF NOT EXISTS ai_flag_reason VARCHAR(255);
ALTER TABLE blood_requests ADD COLUMN IF NOT EXISTS triaged_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_requests_fraud_risk ON blood_requests(fraud_risk_score DESC);
