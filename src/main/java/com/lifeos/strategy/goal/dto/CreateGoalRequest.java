package com.lifeos.strategy.goal.dto;

import com.lifeos.strategy.goal.entity.GoalDomain;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** {@code POST /api/strategy/goals}. The new goal is ACTIVE and goes to the end of the priority order. */
public record CreateGoalRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull GoalDomain domain,
        @Size(max = 2000) String whyItMatters,
        @Size(max = 2000) String successDefinition,
        @NotNull LocalDate windowStart,
        @NotNull LocalDate windowEnd) {
}
