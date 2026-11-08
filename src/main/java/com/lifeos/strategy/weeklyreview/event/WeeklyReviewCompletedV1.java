package com.lifeos.strategy.weeklyreview.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Payload of {@code WeeklyReviewCompleted} v1 (lifeos-contracts: schemas/strategy/WeeklyReviewCompleted.v1.schema.json).
 */
public record WeeklyReviewCompletedV1(
        UUID weeklyReviewId,
        UUID weeklyPlanId,
        String isoWeek,
        LocalDate weekStart,
        int capacityMinutes,
        ActionsV1 actions,
        List<GoalV1> goals,
        int evidenceCount,
        int energy,
        String wentWell,
        String gotStuck,
        String lesson,
        String nextWeekFocus,
        Instant completedAt) {

    public static final String TYPE = "WeeklyReviewCompleted";
    public static final int VERSION = 1;

    public record ActionsV1(int planned, int done, int deferred, int dropped, int plannedMinutes, int doneMinutes) {
    }

    public record GoalV1(UUID goalId, int allocatedMinutes, int doneMinutes) {
    }
}
