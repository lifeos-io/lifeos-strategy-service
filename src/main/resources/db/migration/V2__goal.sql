-- A user's goals. Several can be active at once; priority orders them (1 = most important).
CREATE TABLE goal (
    id                 uuid        PRIMARY KEY,
    user_id            uuid        NOT NULL REFERENCES user_profile (user_id),
    title              text        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 200),
    domain             text        NOT NULL CHECK (domain IN ('CAREER', 'LEARNING', 'FINANCE', 'HEALTH', 'OTHER')),
    why_it_matters     text        CHECK (char_length(why_it_matters) <= 2000),
    success_definition text        CHECK (char_length(success_definition) <= 2000),
    window_start       date        NOT NULL,
    window_end         date        NOT NULL,
    status             text        NOT NULL CHECK (status IN ('ACTIVE', 'PAUSED', 'ACHIEVED', 'ABANDONED')),
    priority           integer     NOT NULL CHECK (priority >= 1),
    version            integer     NOT NULL DEFAULT 0,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    CHECK (window_end >= window_start)
);

CREATE INDEX goal_user_priority_idx ON goal (user_id, priority);
