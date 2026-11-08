package com.lifeos.strategy.strategyversion.dto;

import com.lifeos.strategy.strategyversion.entity.EffortCategory;
import com.lifeos.strategy.strategyversion.entity.PhaseStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record StrategyVersionResponse(
        UUID id,
        UUID goalId,
        int versionNo,
        String summary,
        String changeReason,
        UUID sourceProposalId,
        boolean current,
        Instant createdAt,
        List<PhaseResponse> phases,
        Map<EffortCategory, Integer> effortAllocation) {

    public record PhaseResponse(
            UUID phaseKey,
            int ordinal,
            String title,
            LocalDate startsOn,
            LocalDate endsOn,
            String exitCriteria,
            PhaseStatus status) {
    }
}
