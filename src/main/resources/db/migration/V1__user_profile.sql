-- One row per user, keyed by the Keycloak subject. Created on the user's first request with defaults;
-- onboarding fills in the rest.
CREATE TABLE user_profile (
    user_id                 uuid        PRIMARY KEY,
    display_name            text        CHECK (char_length(display_name) <= 100),
    timezone                text        NOT NULL,
    locale                  text        NOT NULL,
    default_weekly_minutes  integer     NOT NULL CHECK (default_weekly_minutes BETWEEN 0 AND 10080),
    onboarding_completed_at timestamptz,
    version                 integer     NOT NULL DEFAULT 0,
    created_at              timestamptz NOT NULL,
    updated_at              timestamptz NOT NULL
);
