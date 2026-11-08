package com.lifeos.strategy.weeklyreview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/strategy/weekly-plans/{isoWeek}/review}: the user's reflection. Writing it closes the week.
 *
 * @param energy        how the week felt, 1 (drained) to 5 (energised)
 * @param nextWeekFocus one line on what next week is about
 */
public record CompleteWeeklyReviewRequest(
        @NotNull @Min(1) @Max(5) Integer energy,
        @Size(max = 2000) String wentWell,
        @Size(max = 2000) String gotStuck,
        @Size(max = 2000) String lesson,
        @Size(max = 500) String nextWeekFocus) {
}
