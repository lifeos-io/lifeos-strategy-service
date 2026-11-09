package com.lifeos.strategy.decision.entity;

/**
 * DRAFT (still weighing) → OPEN (decided, being lived with) → CLOSED (looked back on). An OPEN decision that was
 * reopened becomes SUPERSEDED once the decision reopened from it is decided.
 */
public enum DecisionStatus {
    DRAFT,
    OPEN,
    CLOSED,
    SUPERSEDED;

    /** Decided decisions (being lived with, or looked back on) can be reopened. */
    public boolean canBeReopened() {
        return this == OPEN || this == CLOSED;
    }
}
