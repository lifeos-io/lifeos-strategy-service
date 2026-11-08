package com.lifeos.strategy.idea.dto;

import com.lifeos.strategy.idea.entity.IdeaStatus;
import com.lifeos.strategy.idea.entity.IdeaVerdict;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An idea with all its evaluations, oldest first. */
public record IdeaResponse(
        UUID id,
        UUID goalId,
        String title,
        String note,
        IdeaStatus status,
        List<EvaluationResponse> evaluations,
        Instant activatedAt,
        Instant closedAt,
        UUID promotedGoalId,
        int version,
        Instant createdAt,
        Instant updatedAt) {

    public record EvaluationResponse(int goalFit, int evidencePotential, int energy, int weeklyMinutes,
            IdeaVerdict verdict, String note, Instant evaluatedAt) {
    }
}
