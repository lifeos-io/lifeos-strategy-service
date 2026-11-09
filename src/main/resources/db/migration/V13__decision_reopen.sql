-- Reopening a decision: a decided (OPEN or CLOSED) decision is never edited; reopening starts a new DRAFT from its
-- content that points back at it. When that draft is decided, an OPEN original becomes SUPERSEDED (a CLOSED one
-- already has its look-back and stays CLOSED). A decision is reopened at most once; the new one can be reopened in
-- turn, which makes a chain.
ALTER TABLE decision
    ADD COLUMN reopened_from_id uuid UNIQUE REFERENCES decision (id),
    ADD COLUMN reopen_reason    text CHECK (char_length(reopen_reason) BETWEEN 1 AND 2000),
    ADD COLUMN superseded_at    timestamptz;

ALTER TABLE decision DROP CONSTRAINT decision_status_check;
ALTER TABLE decision ADD CONSTRAINT decision_status_check
    CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED', 'SUPERSEDED'));

ALTER TABLE decision ADD CONSTRAINT decision_superseded_check
    CHECK ((status = 'SUPERSEDED') = (superseded_at IS NOT NULL));
ALTER TABLE decision ADD CONSTRAINT decision_reopen_check
    CHECK ((reopened_from_id IS NULL) = (reopen_reason IS NULL));

-- "Which open decisions rest on this knowledge item?" when an item is falsified.
CREATE INDEX decision_assumption_item_idx ON decision_assumption (knowledge_item_id);
