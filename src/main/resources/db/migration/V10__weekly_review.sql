-- The look-back that closes a week: the numbers (counted when the review is written) and the user's reflection.
-- One per weekly plan, written once and never changed, like evidence.
CREATE TABLE weekly_review (
    id                 uuid        PRIMARY KEY,
    user_id            uuid        NOT NULL REFERENCES user_profile (user_id),
    weekly_plan_id     uuid        NOT NULL UNIQUE REFERENCES weekly_plan (id),
    iso_week           text        NOT NULL CHECK (iso_week ~ '^[0-9]{4}-W[0-9]{2}$'),
    capacity_minutes   integer     NOT NULL CHECK (capacity_minutes BETWEEN 0 AND 10080),
    -- The week's actions by status, and minutes as in ActionStatusChanged.week.
    planned_count      integer     NOT NULL CHECK (planned_count >= 0),
    done_count         integer     NOT NULL CHECK (done_count >= 0),
    deferred_count     integer     NOT NULL CHECK (deferred_count >= 0),
    dropped_count      integer     NOT NULL CHECK (dropped_count >= 0),
    planned_minutes    integer     NOT NULL CHECK (planned_minutes >= 0),
    done_minutes       integer     NOT NULL CHECK (done_minutes >= 0),
    -- Evidence that occurred within the week.
    evidence_count     integer     NOT NULL CHECK (evidence_count >= 0),
    energy             integer     NOT NULL CHECK (energy BETWEEN 1 AND 5),
    went_well          text        CHECK (char_length(went_well) <= 2000),
    got_stuck          text        CHECK (char_length(got_stuck) <= 2000),
    lesson             text        CHECK (char_length(lesson) <= 2000),
    next_week_focus    text        CHECK (char_length(next_week_focus) <= 500),
    completed_at       timestamptz NOT NULL,
    created_at         timestamptz NOT NULL
);

CREATE INDEX weekly_review_user_idx ON weekly_review (user_id, iso_week DESC);

-- How each goal of the week's split fared: its minutes, and the estimates of its done actions
-- (an action serving two goals counts for both).
CREATE TABLE weekly_review_goal (
    weekly_review_id  uuid    NOT NULL REFERENCES weekly_review (id) ON DELETE CASCADE,
    goal_id           uuid    NOT NULL REFERENCES goal (id),
    allocated_minutes integer NOT NULL CHECK (allocated_minutes BETWEEN 0 AND 10080),
    done_minutes      integer NOT NULL CHECK (done_minutes >= 0),
    PRIMARY KEY (weekly_review_id, goal_id)
);
