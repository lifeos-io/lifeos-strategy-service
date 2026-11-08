package com.lifeos.strategy.weeklyplan.event;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Payload of {@code WeeklyPlanActivated} v1 (lifeos-contracts: schemas/strategy/WeeklyPlanActivated.v1.schema.json). */
public record WeeklyPlanActivatedV1(
        UUID weeklyPlanId,
        String isoWeek,
        LocalDate weekStart,
        int capacityMinutes,
        String headline,
        List<AllocationV1> allocations,
        int plannedActionCount,
        int plannedActionMinutes) {

    public static final String TYPE = "WeeklyPlanActivated";
    public static final int VERSION = 1;

    public record AllocationV1(UUID goalId, UUID strategyVersionId, int allocatedMinutes) {
    }
}
