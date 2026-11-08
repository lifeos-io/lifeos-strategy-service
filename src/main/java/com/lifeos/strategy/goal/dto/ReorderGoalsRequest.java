package com.lifeos.strategy.goal.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/**
 * {@code PUT /api/strategy/goals/priority-order}: every open (active or paused) goal, most important first.
 * The goals get priorities 1, 2, 3... in this order.
 */
public record ReorderGoalsRequest(@NotEmpty List<UUID> goalIds) {
}
