package com.lifeos.strategy.weeklyplan.entity;

import jakarta.persistence.Embeddable;
import java.util.Objects;
import java.util.UUID;

/** Minutes of a week given to one goal, and the goal's strategy version the week was planned with. */
@Embeddable
public class GoalAllocation {

    private UUID goalId;
    private UUID strategyVersionId;
    private int allocatedMinutes;

    protected GoalAllocation() {
    }

    public GoalAllocation(UUID goalId, UUID strategyVersionId, int allocatedMinutes) {
        this.goalId = goalId;
        this.strategyVersionId = strategyVersionId;
        this.allocatedMinutes = allocatedMinutes;
    }

    public UUID getGoalId() {
        return goalId;
    }

    public UUID getStrategyVersionId() {
        return strategyVersionId;
    }

    public int getAllocatedMinutes() {
        return allocatedMinutes;
    }

    // Value equality: Hibernate compares element collections with equals(); without it every flush would see the
    // allocations as changed, rewrite them and bump the plan's version.
    @Override
    public boolean equals(Object other) {
        return other instanceof GoalAllocation that
                && Objects.equals(goalId, that.goalId)
                && Objects.equals(strategyVersionId, that.strategyVersionId)
                && allocatedMinutes == that.allocatedMinutes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(goalId, strategyVersionId, allocatedMinutes);
    }
}
