-- Ideas the user captured, so they are not lost and do not steal focus. At most two are ACTIVE at a time
-- (checked in the service under a per-user lock: a partial unique index can only say "at most one").
CREATE TABLE idea (
    id               uuid        PRIMARY KEY,
    user_id          uuid        NOT NULL REFERENCES user_profile (user_id),
    -- The goal the idea would serve, if any.
    goal_id          uuid        REFERENCES goal (id),
    title            text        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 200),
    note             text        CHECK (char_length(note) <= 4000),
    status           text        NOT NULL CHECK (status IN ('INBOX', 'ACTIVE', 'SHELVED', 'DROPPED', 'PROMOTED')),
    -- The last time the idea became ACTIVE; kept when it is shelved, as history.
    activated_at     timestamptz,
    closed_at        timestamptz,
    -- The goal the idea grew into.
    promoted_goal_id uuid        REFERENCES goal (id),
    version          integer     NOT NULL DEFAULT 0,
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL,
    CHECK (status <> 'ACTIVE' OR activated_at IS NOT NULL),
    CHECK ((status IN ('DROPPED', 'PROMOTED')) = (closed_at IS NOT NULL)),
    CHECK ((status = 'PROMOTED') = (promoted_goal_id IS NOT NULL))
);

CREATE INDEX idea_user_idx ON idea (user_id, status);

-- Evaluations of an idea, oldest first. Append-only: the latest one decides whether the idea may become ACTIVE.
CREATE TABLE idea_evaluation (
    idea_id            uuid        NOT NULL REFERENCES idea (id) ON DELETE CASCADE,
    position           integer     NOT NULL,
    goal_fit           integer     NOT NULL CHECK (goal_fit BETWEEN 1 AND 5),
    evidence_potential integer     NOT NULL CHECK (evidence_potential BETWEEN 1 AND 5),
    energy             integer     NOT NULL CHECK (energy BETWEEN 1 AND 5),
    weekly_minutes     integer     NOT NULL CHECK (weekly_minutes BETWEEN 0 AND 10080),
    verdict            text        NOT NULL CHECK (verdict IN ('PURSUE', 'PARK', 'DROP')),
    note               text        CHECK (char_length(note) <= 2000),
    evaluated_at       timestamptz NOT NULL,
    PRIMARY KEY (idea_id, position)
);
