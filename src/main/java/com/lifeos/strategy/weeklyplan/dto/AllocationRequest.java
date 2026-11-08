package com.lifeos.strategy.weeklyplan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Minutes of the week given to one of the user's active goals. */
public record AllocationRequest(
        @NotNull UUID goalId,
        @NotNull @Min(0) @Max(10080) Integer allocatedMinutes) {
}
