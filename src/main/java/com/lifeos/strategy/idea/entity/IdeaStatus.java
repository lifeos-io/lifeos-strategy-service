package com.lifeos.strategy.idea.entity;

/**
 * INBOX (captured) → ACTIVE (being pursued, at most two at a time) → PROMOTED (grew into a goal).
 * INBOX and ACTIVE can be SHELVED (and reopened to INBOX) or DROPPED. DROPPED and PROMOTED are final.
 */
public enum IdeaStatus {
    INBOX,
    ACTIVE,
    SHELVED,
    DROPPED,
    PROMOTED;

    /** At most this many ideas of a user are ACTIVE at a time. */
    public static final int MAX_ACTIVE = 2;

    /** Dropped and promoted ideas are read-only. */
    public boolean isClosed() {
        return this == DROPPED || this == PROMOTED;
    }

    public boolean canChangeTo(IdeaStatus target) {
        return switch (this) {
            case INBOX -> target == ACTIVE || target == SHELVED || target == DROPPED;
            case ACTIVE -> target == SHELVED || target == DROPPED || target == PROMOTED;
            case SHELVED -> target == INBOX || target == DROPPED;
            case DROPPED, PROMOTED -> false;
        };
    }
}
