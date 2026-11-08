package com.lifeos.strategy.idea.dto;

import com.lifeos.strategy.idea.entity.IdeaVerdict;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/strategy/ideas/{ideaId}/evaluations}: one more look at the idea (evaluations are appended).
 *
 * @param goalFit           1–5: how much it serves the open goals
 * @param evidencePotential 1–5: how likely it is to produce evidence for a goal
 * @param energy            1–5: how much the user wants to do it
 * @param weeklyMinutes     estimated time per week while it is active
 */
public record EvaluateIdeaRequest(
        @NotNull @Min(1) @Max(5) Integer goalFit,
        @NotNull @Min(1) @Max(5) Integer evidencePotential,
        @NotNull @Min(1) @Max(5) Integer energy,
        @NotNull @Min(0) @Max(10080) Integer weeklyMinutes,
        @NotNull IdeaVerdict verdict,
        @Size(max = 2000) String note) {
}
