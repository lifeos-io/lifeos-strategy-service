package com.lifeos.strategy.proposal.dto;

import com.lifeos.strategy.proposal.entity.ProposalKind;
import com.lifeos.strategy.proposal.entity.ProposalStatus;
import com.lifeos.strategy.proposal.entity.SourceType;
import com.lifeos.strategy.strategyversion.entity.EffortCategory;
import com.lifeos.strategy.strategyversion.entity.PhaseStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A proposal with the strategy version it would create.
 *
 * @param applicable whether accepting it would work now: it is pending, the goal is open and its strategy is still
 *                   the version Coach based it on
 */
public record ChangeProposalResponse(
        UUID id,
        UUID recommendationId,
        ProposalKind kind,
        UUID goalId,
        Integer basedOnVersionNo,
        String rationale,
        List<SourceResponse> sources,
        RevisionResponse revision,
        ProposalStatus status,
        boolean applicable,
        String decisionNote,
        Instant decidedAt,
        UUID appliedStrategyVersionId,
        Instant createdAt) {

    public record SourceResponse(SourceType type, UUID id) {
    }

    public record RevisionResponse(String summary, String changeReason, List<PhaseResponse> phases,
            Map<EffortCategory, Integer> effortAllocation) {
    }

    public record PhaseResponse(UUID phaseKey, String title, LocalDate startsOn, LocalDate endsOn,
            String exitCriteria, PhaseStatus status) {
    }
}
