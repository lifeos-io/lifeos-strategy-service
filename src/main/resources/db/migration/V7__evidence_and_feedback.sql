-- Feedback the user received. Append-only: it records what someone said, when.
CREATE TABLE feedback (
    id           uuid        PRIMARY KEY,
    user_id      uuid        NOT NULL REFERENCES user_profile (user_id),
    source_kind  text        NOT NULL CHECK (source_kind IN ('MANAGER', 'PEER', 'CODE_REVIEW', 'SELF', 'OTHER')),
    source_label text        CHECK (char_length(source_label) <= 200),
    content      text        NOT NULL CHECK (char_length(content) BETWEEN 1 AND 4000),
    reliability  text        NOT NULL CHECK (reliability IN ('HIGH', 'MEDIUM', 'LOW')),
    received_on  date        NOT NULL,
    created_at   timestamptz NOT NULL
);

CREATE INDEX feedback_user_idx ON feedback (user_id, received_on DESC);

-- Evidence of progress, as a chain: activity -> output -> outcome -> impact (parent = the lower step).
-- Append-only like feedback.
CREATE TABLE evidence (
    id                 uuid        PRIMARY KEY,
    user_id            uuid        NOT NULL REFERENCES user_profile (user_id),
    title              text        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 200),
    level              text        NOT NULL CHECK (level IN ('ACTIVITY', 'OUTPUT', 'OUTCOME', 'IMPACT')),
    strength           text        NOT NULL
        CHECK (strength IN ('SELF_REPORT', 'PERSONAL_PROJECT', 'WORK_OUTPUT', 'EXTERNAL_FEEDBACK')),
    occurred_on        date        NOT NULL,
    parent_evidence_id uuid        REFERENCES evidence (id),
    -- A draft week's action can be deleted; the evidence stays.
    action_id          uuid        REFERENCES action (id) ON DELETE SET NULL,
    feedback_id        uuid        REFERENCES feedback (id),
    note               text        CHECK (char_length(note) <= 2000),
    created_at         timestamptz NOT NULL
);

CREATE INDEX evidence_user_idx ON evidence (user_id, occurred_on DESC);

-- The goals the evidence moves forward.
CREATE TABLE evidence_goal (
    evidence_id uuid NOT NULL REFERENCES evidence (id) ON DELETE CASCADE,
    goal_id     uuid NOT NULL REFERENCES goal (id),
    PRIMARY KEY (evidence_id, goal_id)
);

-- The phases whose exit criteria the evidence counts towards, by stable phase key (survives revisions).
CREATE TABLE evidence_phase (
    evidence_id uuid NOT NULL REFERENCES evidence (id) ON DELETE CASCADE,
    phase_key   uuid NOT NULL,
    PRIMARY KEY (evidence_id, phase_key)
);
