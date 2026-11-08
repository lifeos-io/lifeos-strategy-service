-- Long-term decisions: what was weighed, what was chosen and why, what it rests on, when to look back.
-- Decision quality (would_decide_again) is kept apart from the outcome (outcome_note).
CREATE TABLE decision (
    id                 uuid        PRIMARY KEY,
    user_id            uuid        NOT NULL REFERENCES user_profile (user_id),
    goal_id            uuid        REFERENCES goal (id),
    title              text        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 200),
    context            text        CHECK (char_length(context) <= 4000),
    status             text        NOT NULL CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED')),
    expected_outcome   text        CHECK (char_length(expected_outcome) <= 2000),
    decided_at         timestamptz,
    review_on          date,
    outcome_note       text        CHECK (char_length(outcome_note) <= 4000),
    would_decide_again text        CHECK (would_decide_again IN ('YES', 'NO', 'GOOD_CALL_BAD_LUCK')),
    closed_at          timestamptz,
    version            integer     NOT NULL DEFAULT 0,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    -- A decided (OPEN or CLOSED) decision has its decision data; a CLOSED one also its look-back.
    CHECK (status = 'DRAFT' OR (decided_at IS NOT NULL AND review_on IS NOT NULL AND expected_outcome IS NOT NULL)),
    CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL AND would_decide_again IS NOT NULL))
);

CREATE INDEX decision_user_idx ON decision (user_id, status);

-- The alternatives weighed, labelled A, B, C... in order.
CREATE TABLE decision_alternative (
    decision_id      uuid    NOT NULL REFERENCES decision (id) ON DELETE CASCADE,
    position         integer NOT NULL,
    label            text    NOT NULL CHECK (label ~ '^[A-Z]$'),
    description      text    NOT NULL CHECK (char_length(description) BETWEEN 1 AND 1000),
    is_chosen        boolean NOT NULL,
    rejection_reason text    CHECK (char_length(rejection_reason) <= 1000),
    PRIMARY KEY (decision_id, position),
    UNIQUE (decision_id, label)
);

-- The knowledge items (usually assumptions) a decision rests on: if one is falsified, the decision is suspect.
CREATE TABLE decision_assumption (
    decision_id       uuid NOT NULL REFERENCES decision (id) ON DELETE CASCADE,
    knowledge_item_id uuid NOT NULL REFERENCES knowledge_item (id),
    PRIMARY KEY (decision_id, knowledge_item_id)
);

-- What should make the user reconsider: a date, or an event ("if I get assigned to the K8s project").
CREATE TABLE decision_reopen_trigger (
    decision_id uuid    NOT NULL REFERENCES decision (id) ON DELETE CASCADE,
    position    integer NOT NULL,
    kind        text    NOT NULL CHECK (kind IN ('DATE', 'EVENT')),
    description text    NOT NULL CHECK (char_length(description) BETWEEN 1 AND 500),
    event_type  text    CHECK (event_type ~ '^[A-Z][A-Za-z0-9]*$'),
    fires_on    date,
    fired_at    timestamptz,
    PRIMARY KEY (decision_id, position),
    CHECK (kind = 'EVENT' OR fires_on IS NOT NULL)
);
