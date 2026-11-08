-- A goal's knowledge map: facts, assumptions and unknowns, and how far each one has been verified.
-- Decisions will rest on assumptions from here; a falsified assumption is a reason to reopen them.
CREATE TABLE knowledge_item (
    id            uuid        PRIMARY KEY,
    goal_id       uuid        NOT NULL REFERENCES goal (id),
    user_id       uuid        NOT NULL,
    statement     text        NOT NULL CHECK (char_length(statement) BETWEEN 1 AND 500),
    kind          text        NOT NULL CHECK (kind IN ('FACT', 'ASSUMPTION', 'UNKNOWN')),
    status        text        NOT NULL CHECK (status IN ('UNVERIFIED', 'PARTIAL', 'VERIFIED', 'FALSIFIED')),
    is_external   boolean     NOT NULL,
    source        text        CHECK (char_length(source) <= 500),
    how_to_verify text        CHECK (char_length(how_to_verify) <= 1000),
    verified_at   timestamptz,
    version       integer     NOT NULL DEFAULT 0,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    -- verified_at is the moment the item was settled (verified or falsified), and only then.
    CHECK ((status IN ('VERIFIED', 'FALSIFIED')) = (verified_at IS NOT NULL))
);

CREATE INDEX knowledge_item_goal_idx ON knowledge_item (goal_id);
