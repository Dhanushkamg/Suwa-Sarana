-- V50__ai_triage_records.sql
-- Append-only record of every AI triage evaluation (score + ordered reasons) and of the human review that followed.
-- The original AI score is never overwritten or zeroed: a review only fills the reviewed_* columns.

CREATE TABLE IF NOT EXISTS ai_triage_records (
    id              BIGSERIAL PRIMARY KEY,
    request_id      BIGINT       NOT NULL REFERENCES blood_requests(id) ON DELETE CASCADE,
    score           INTEGER      NOT NULL,
    raw_score       INTEGER      NOT NULL,
    reasons_json    TEXT         NOT NULL,
    engine_version  VARCHAR(30)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by     BIGINT,
    reviewed_at     TIMESTAMP,
    review_decision VARCHAR(20),
    review_note     VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_ai_triage_request ON ai_triage_records(request_id, created_at DESC);
