package com.lifeos.strategy.goal.entity;

import java.util.Set;

/**
 * ACTIVE ⇄ PAUSED; both can end as ACHIEVED or ABANDONED, which are final.
 */
public enum GoalStatus {
    ACTIVE,
    PAUSED,
    ACHIEVED,
    ABANDONED;

    /** Goals that still compete for the user's capacity and take part in the priority order. */
    public static final Set<GoalStatus> OPEN = Set.of(ACTIVE, PAUSED);

    public boolean isOpen() {
        return OPEN.contains(this);
    }

    public boolean canChangeTo(GoalStatus target) {
        return this != target && isOpen();
    }
}
