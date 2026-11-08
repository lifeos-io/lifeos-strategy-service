package com.lifeos.strategy.weeklyplan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * {@code PATCH /api/strategy/weekly-plans/{isoWeek}}: only the fields present change; {@code allocations},
 * when present, replaces the whole split. Draft and active weeks can change, closed ones cannot.
 *
 * @param version the version the client last read (409 {@code CONCURRENT_MODIFICATION} when it is stale)
 */
public record UpdateWeeklyPlanRequest(
        @NotNull Integer version,
        @Min(0) @Max(10080) Integer capacityMinutes,
        @Size(max = 200) String headline,
        @Size(max = 20) List<@Valid @NotNull AllocationRequest> allocations) {
}
