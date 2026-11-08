package com.lifeos.strategy.weeklyplan.dto;

import com.lifeos.strategy.weeklyplan.entity.WeeklyPlanStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @param allocatedMinutes the total given to goals; {@code capacityMinutes - allocatedMinutes} is still free
 */
public record WeeklyPlanResponse(
        UUID id,
        String isoWeek,
        LocalDate weekStart,
        int capacityMinutes,
        int allocatedMinutes,
        String headline,
        WeeklyPlanStatus status,
        Instant activatedAt,
        Instant closedAt,
        int version,
        List<AllocationResponse> allocations,
        Instant createdAt,
        Instant updatedAt) {

    /** @param strategyVersionId the goal's strategy version the week was planned with (set on activation) */
    public record AllocationResponse(UUID goalId, UUID strategyVersionId, int allocatedMinutes) {
    }
}
