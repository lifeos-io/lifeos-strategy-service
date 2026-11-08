package com.lifeos.strategy.weeklyplan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * {@code POST /api/strategy/weekly-plans}: a draft for one week.
 *
 * @param isoWeek         e.g. {@code 2026-W46}
 * @param capacityMinutes defaults to the profile's {@code defaultWeeklyMinutes}
 * @param allocations     the split across goals; the total may not exceed the capacity
 */
public record CreateWeeklyPlanRequest(
        @NotBlank String isoWeek,
        @Min(0) @Max(10080) Integer capacityMinutes,
        @Size(max = 200) String headline,
        @NotNull @Size(max = 20) List<@Valid @NotNull AllocationRequest> allocations) {
}
