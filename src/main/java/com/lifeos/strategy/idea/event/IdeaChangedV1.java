package com.lifeos.strategy.idea.event;

import com.lifeos.strategy.idea.entity.IdeaStatus;
import com.lifeos.strategy.idea.entity.IdeaVerdict;
import java.time.Instant;
import java.util.UUID;

/** Payload of {@code IdeaChanged} v1 (lifeos-contracts: schemas/strategy/IdeaChanged.v1.schema.json). */
public record IdeaChangedV1(
        UUID ideaId,
        UUID goalId,
        String title,
        String note,
        IdeaStatus status,
        Instant activatedAt,
        Instant closedAt,
        UUID promotedGoalId,
        EvaluationV1 latestEvaluation,
        int evaluationCount,
        int version) {

    public static final String TYPE = "IdeaChanged";
    public static final int VERSION = 1;

    public record EvaluationV1(int goalFit, int evidencePotential, int energy, int weeklyMinutes,
            IdeaVerdict verdict, String note, Instant evaluatedAt) {
    }
}
