-- Changes Coach proposes (RecommendationProposed). Coach never changes the plan itself: a proposal waits for the
-- user, and only an accepted one is applied. v1 knows one kind: a complete new strategy version for a goal.
CREATE TABLE change_proposal (
    id                          uuid        PRIMARY KEY,
    user_id                     uuid        NOT NULL REFERENCES user_profile (user_id),
    -- Coach's ID; a redelivered or repeated recommendation is stored once.
    recommendation_id           uuid        NOT NULL UNIQUE,
    kind                        text        NOT NULL CHECK (kind IN ('STRATEGY_REVISION')),
    goal_id                     uuid        NOT NULL REFERENCES goal (id),
    -- The current strategy version Coach saw; null when the goal had no strategy yet.
    based_on_version_no         integer     CHECK (based_on_version_no >= 1),
    rationale                   text        NOT NULL CHECK (char_length(rationale) BETWEEN 1 AND 2000),
    -- The proposed version as Coach sent it (summary, changeReason, phases, effortAllocation). A snapshot that
    -- is never queried into: applying it goes through the same validation as a revision the user writes.
    revision                    jsonb       NOT NULL,
    status                      text        NOT NULL
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'SUPERSEDED')),
    decision_note               text        CHECK (char_length(decision_note) <= 1000),
    decided_at                  timestamptz,
    applied_strategy_version_id uuid        REFERENCES strategy_version (id),
    version                     integer     NOT NULL DEFAULT 0,
    created_at                  timestamptz NOT NULL,
    updated_at                  timestamptz NOT NULL,
    CHECK ((status = 'PENDING') = (decided_at IS NULL)),
    CHECK ((status = 'ACCEPTED') = (applied_strategy_version_id IS NOT NULL))
);

-- At most one proposal per goal waits for the user; a newer one supersedes it.
CREATE UNIQUE INDEX change_proposal_one_pending_idx ON change_proposal (goal_id) WHERE status = 'PENDING';
CREATE INDEX change_proposal_user_idx ON change_proposal (user_id, status);

-- What prompted the proposal (feedback, evidence, a weekly review...).
CREATE TABLE change_proposal_source (
    change_proposal_id uuid NOT NULL REFERENCES change_proposal (id) ON DELETE CASCADE,
    source_type        text NOT NULL
        CHECK (source_type IN ('FEEDBACK', 'EVIDENCE', 'WEEKLY_REVIEW', 'DECISION', 'KNOWLEDGE_ITEM')),
    source_id          uuid NOT NULL,
    PRIMARY KEY (change_proposal_id, source_type, source_id)
);

-- Announced in V3: a revision records the proposal it came from.
ALTER TABLE strategy_version
    ADD CONSTRAINT strategy_version_source_proposal_fk
    FOREIGN KEY (source_proposal_id) REFERENCES change_proposal (id);
