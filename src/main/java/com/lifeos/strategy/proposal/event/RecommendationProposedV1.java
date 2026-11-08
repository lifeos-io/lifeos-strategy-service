package com.lifeos.strategy.proposal.event;

import com.lifeos.strategy.proposal.entity.ProposalKind;
import com.lifeos.strategy.proposal.entity.SourceType;
import com.lifeos.strategy.strategyversion.entity.EffortCategory;
import com.lifeos.strategy.strategyversion.entity.PhaseStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payload of Coach's {@code RecommendationProposed} v1, consumed from {@code lifeos.coach.events.v1}
 * (lifeos-contracts: schemas/coach/RecommendationProposed.v1.schema.json).
 */
public record RecommendationProposedV1(
        UUID recommendationId,
        ProposalKind kind,
        UUID goalId,
        Integer basedOnVersionNo,
        String rationale,
        List<SourceV1> sources,
        RevisionV1 revision) {

    public static final String TYPE = "RecommendationProposed";
    public static final int VERSION = 1;

    public record SourceV1(SourceType type, UUID id) {
    }

    /** The complete proposed strategy version; also the JSON stored in {@code change_proposal.revision}. */
    public record RevisionV1(String summary, String changeReason, List<PhaseV1> phases,
            Map<EffortCategory, Integer> effortAllocation) {
    }

    /** {@code phaseKey} carries over a phase of the version the recommendation is based on; null for a new one. */
    public record PhaseV1(UUID phaseKey, String title, LocalDate startsOn, LocalDate endsOn, String exitCriteria,
            PhaseStatus status) {
    }
}
