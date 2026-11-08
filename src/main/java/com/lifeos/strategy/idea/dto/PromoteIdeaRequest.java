package com.lifeos.strategy.idea.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * {@code POST /api/strategy/ideas/{ideaId}/promote}: the active idea grew into a goal (ACTIVE → PROMOTED).
 *
 * @param goalId the goal it became, created beforehand (one of the user's open goals)
 */
public record PromoteIdeaRequest(@NotNull UUID goalId) {
}
