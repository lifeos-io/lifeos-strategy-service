package com.lifeos.strategy.weeklyreview.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A written review: the week's numbers as they were when it was written, and the user's reflection. */
public record WeeklyReviewResponse(
        UUID id,
        UUID weeklyPlanId,
        String isoWeek,
        int capacityMinutes,
        ActionsResponse actions,
        List<GoalOutcomeResponse> goals,
        int evidenceCount,
        int energy,
        String wentWell,
        String gotStuck,
        String lesson,
        String nextWeekFocus,
        Instant completedAt) {

    /** The week's actions by status; minutes as in {@code ActionStatusChanged.week}. */
    public record ActionsResponse(int planned, int done, int deferred, int dropped, int plannedMinutes,
            int doneMinutes) {
    }

    /** One goal of the week's split: its minutes and the estimates of its done actions. */
    public record GoalOutcomeResponse(UUID goalId, int allocatedMinutes, int doneMinutes) {
    }
}
