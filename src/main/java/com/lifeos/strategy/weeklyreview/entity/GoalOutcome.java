package com.lifeos.strategy.weeklyreview.entity;

import jakarta.persistence.Embeddable;
import java.util.Objects;
import java.util.UUID;

/**
 * How one goal of the week's split fared: the minutes it was given and the estimates of its done actions (an action
 * serving two goals counts for both).
 */
@Embeddable
public class GoalOutcome {

    private UUID goalId;
    private int allocatedMinutes;
    private int doneMinutes;

    protected GoalOutcome() {
    }

    public GoalOutcome(UUID goalId, int allocatedMinutes, int doneMinutes) {
        this.goalId = Objects.requireNonNull(goalId);
        this.allocatedMinutes = allocatedMinutes;
        this.doneMinutes = doneMinutes;
    }

    public UUID getGoalId() {
        return goalId;
    }

    public int getAllocatedMinutes() {
        return allocatedMinutes;
    }

    public int getDoneMinutes() {
        return doneMinutes;
    }

    // Value equality, so Hibernate does not rewrite the collection on every flush.
    @Override
    public boolean equals(Object other) {
        return other instanceof GoalOutcome that
                && allocatedMinutes == that.allocatedMinutes
                && doneMinutes == that.doneMinutes
                && goalId.equals(that.goalId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(goalId, allocatedMinutes, doneMinutes);
    }
}
