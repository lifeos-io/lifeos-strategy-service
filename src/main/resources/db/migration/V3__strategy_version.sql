-- How a goal will be reached. Versions are immutable: a revision adds a new row and becomes the current one.
CREATE TABLE strategy_version (
    id                 uuid        PRIMARY KEY,
    goal_id            uuid        NOT NULL REFERENCES goal (id),
    user_id            uuid        NOT NULL,
    version_no         integer     NOT NULL CHECK (version_no >= 1),
    summary            text        NOT NULL CHECK (char_length(summary) BETWEEN 1 AND 2000),
    change_reason      text        NOT NULL CHECK (char_length(change_reason) BETWEEN 1 AND 2000),
    -- The change proposal the revision came from; the foreign key is added with the change_proposal table.
    source_proposal_id uuid,
    is_current         boolean     NOT NULL,
    created_at         timestamptz NOT NULL,
    UNIQUE (goal_id, version_no)
);

-- At most one current version per goal.
CREATE UNIQUE INDEX strategy_version_current_idx ON strategy_version (goal_id) WHERE is_current;

-- Phases are copied into every version. phase_key stays the same for "the same phase" across versions, so
-- actions and evidence linked to a phase keep their link after a revision.
CREATE TABLE phase (
    id                  uuid    PRIMARY KEY,
    strategy_version_id uuid    NOT NULL REFERENCES strategy_version (id) ON DELETE CASCADE,
    phase_key           uuid    NOT NULL,
    ordinal             integer NOT NULL CHECK (ordinal >= 1),
    title               text    NOT NULL CHECK (char_length(title) BETWEEN 1 AND 200),
    starts_on           date    NOT NULL,
    ends_on             date    NOT NULL,
    exit_criteria       text    CHECK (char_length(exit_criteria) <= 2000),
    status              text    NOT NULL CHECK (status IN ('UPCOMING', 'ACTIVE', 'DONE')),
    CHECK (ends_on >= starts_on),
    UNIQUE (strategy_version_id, ordinal),
    UNIQUE (strategy_version_id, phase_key)
);

CREATE INDEX phase_key_idx ON phase (phase_key);

-- "Fuel split" of a version; the percentages add up to 100 (checked in the service).
CREATE TABLE effort_allocation (
    strategy_version_id uuid    NOT NULL REFERENCES strategy_version (id) ON DELETE CASCADE,
    category            text    NOT NULL CHECK (category IN ('TECHNICAL', 'DOMAIN', 'OTHER')),
    percent             integer NOT NULL CHECK (percent BETWEEN 0 AND 100),
    PRIMARY KEY (strategy_version_id, category)
);
