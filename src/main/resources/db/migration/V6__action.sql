-- The concrete work of a week. Estimates of the actions that are not dropped stay within the week's capacity
-- (checked in the service).
CREATE TABLE action (
    id                uuid        PRIMARY KEY,
    weekly_plan_id    uuid        NOT NULL REFERENCES weekly_plan (id) ON DELETE CASCADE,
    user_id           uuid        NOT NULL,
    -- The phase the action moves forward, by its stable key (phase.phase_key) so the link survives revisions.
    phase_key         uuid,
    title             text        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 200),
    why               text        CHECK (char_length(why) <= 1000),
    expected_evidence text        CHECK (char_length(expected_evidence) <= 1000),
    kind              text        NOT NULL
        CHECK (kind IN ('DOMAIN', 'TECHNICAL', 'INFO_GATHERING', 'EVIDENCE', 'REST', 'OTHER')),
    estimate_minutes  integer     NOT NULL CHECK (estimate_minutes BETWEEN 0 AND 10080),
    status            text        NOT NULL CHECK (status IN ('PLANNED', 'DONE', 'DEFERRED', 'DROPPED')),
    deferred_count    integer     NOT NULL DEFAULT 0 CHECK (deferred_count >= 0),
    sort_order        integer     NOT NULL,
    completed_at      timestamptz,
    version           integer     NOT NULL DEFAULT 0,
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    CHECK ((status = 'DONE') = (completed_at IS NOT NULL))
);

CREATE INDEX action_plan_idx ON action (weekly_plan_id, sort_order);

-- An action can serve several goals (e.g. a release that is both product work and portfolio evidence).
CREATE TABLE action_goal (
    action_id uuid NOT NULL REFERENCES action (id) ON DELETE CASCADE,
    goal_id   uuid NOT NULL REFERENCES goal (id),
    PRIMARY KEY (action_id, goal_id)
);
