-- A user's plan for one ISO week: how much time there is and how it is split across goals.
CREATE TABLE weekly_plan (
    id               uuid        PRIMARY KEY,
    user_id          uuid        NOT NULL REFERENCES user_profile (user_id),
    iso_week         text        NOT NULL CHECK (iso_week ~ '^[0-9]{4}-W[0-9]{2}$'),
    week_start       date        NOT NULL CHECK (extract(isodow FROM week_start) = 1),
    capacity_minutes integer     NOT NULL CHECK (capacity_minutes BETWEEN 0 AND 10080),
    headline         text        CHECK (char_length(headline) <= 200),
    status           text        NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED')),
    activated_at     timestamptz,
    closed_at        timestamptz,
    version          integer     NOT NULL DEFAULT 0,
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL,
    UNIQUE (user_id, iso_week)
);

-- A user commits to one week at a time.
CREATE UNIQUE INDEX weekly_plan_one_active_idx ON weekly_plan (user_id) WHERE status = 'ACTIVE';

-- The week's capacity split across goals; the total may not exceed capacity_minutes (checked in the service).
-- strategy_version_id records which strategy the week was planned with (set when the plan is activated).
CREATE TABLE weekly_plan_goal (
    weekly_plan_id      uuid    NOT NULL REFERENCES weekly_plan (id) ON DELETE CASCADE,
    goal_id             uuid    NOT NULL REFERENCES goal (id),
    strategy_version_id uuid    REFERENCES strategy_version (id),
    allocated_minutes   integer NOT NULL CHECK (allocated_minutes BETWEEN 0 AND 10080),
    PRIMARY KEY (weekly_plan_id, goal_id)
);
