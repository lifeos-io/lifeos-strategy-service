package com.lifeos.strategy.strategyversion.event;

import com.lifeos.strategy.strategyversion.entity.EffortCategory;
import com.lifeos.strategy.strategyversion.entity.PhaseStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Payload of {@code StrategyRevised} v1 (lifeos-contracts: schemas/strategy/StrategyRevised.v1.schema.json). */
public record StrategyRevisedV1(
        UUID goalId,
        UUID strategyVersionId,
        int versionNo,
        Integer previousVersionNo,
        String summary,
        String changeReason,
        UUID sourceProposalId,
        List<PhaseV1> phases,
        Map<EffortCategory, Integer> effortAllocation) {

    public static final String TYPE = "StrategyRevised";
    public static final int VERSION = 1;

    public record PhaseV1(
            UUID phaseKey,
            int ordinal,
            String title,
            LocalDate startsOn,
            LocalDate endsOn,
            String exitCriteria,
            PhaseStatus status) {
    }
}
