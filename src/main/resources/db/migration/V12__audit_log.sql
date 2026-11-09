-- Audit trail of sensitive operations (first: KVKK deletion of a user's data and identity account).
-- Append-only, and deliberately outside the user's data: it survives the deletion it records. It holds no
-- personal content: the user's ID, what happened, who did it, and technical details (e.g. row counts).
CREATE TABLE audit_log (
    id             uuid        PRIMARY KEY,
    -- No foreign key: the user's profile is gone after a deletion, the record stays.
    user_id        uuid        NOT NULL,
    action         text        NOT NULL CHECK (action ~ '^[A-Z][A-Z_]*$'),
    -- USER: the user's own request; SYSTEM: a background step (e.g. an event consumer).
    actor          text        NOT NULL CHECK (actor IN ('USER', 'SYSTEM')),
    correlation_id text        CHECK (char_length(correlation_id) <= 64),
    details        jsonb       NOT NULL DEFAULT '{}',
    occurred_at    timestamptz NOT NULL
);

CREATE INDEX audit_log_user_idx ON audit_log (user_id, action, occurred_at);
