package com.lifeos.strategy.weeklyreview.entity;

import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * The week's actions by status, counted when the review is written. Minutes follow
 * {@code ActionStatusChanged.week}: planned = estimates of all actions except dropped ones, done = estimates of
 * done actions.
 */
@Embeddable
public class ActionTally {

    private int plannedCount;
    private int doneCount;
    private int deferredCount;
    private int droppedCount;
    private int plannedMinutes;
    private int doneMinutes;

    protected ActionTally() {
    }

    public ActionTally(int plannedCount, int doneCount, int deferredCount, int droppedCount, int plannedMinutes,
            int doneMinutes) {
        this.plannedCount = plannedCount;
        this.doneCount = doneCount;
        this.deferredCount = deferredCount;
        this.droppedCount = droppedCount;
        this.plannedMinutes = plannedMinutes;
        this.doneMinutes = doneMinutes;
    }

    public int getPlannedCount() {
        return plannedCount;
    }

    public int getDoneCount() {
        return doneCount;
    }

    public int getDeferredCount() {
        return deferredCount;
    }

    public int getDroppedCount() {
        return droppedCount;
    }

    public int getPlannedMinutes() {
        return plannedMinutes;
    }

    public int getDoneMinutes() {
        return doneMinutes;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ActionTally that
                && plannedCount == that.plannedCount
                && doneCount == that.doneCount
                && deferredCount == that.deferredCount
                && droppedCount == that.droppedCount
                && plannedMinutes == that.plannedMinutes
                && doneMinutes == that.doneMinutes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(plannedCount, doneCount, deferredCount, droppedCount, plannedMinutes, doneMinutes);
    }
}
