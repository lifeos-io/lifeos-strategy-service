package com.lifeos.strategy.strategyversion.mapper;

import com.lifeos.strategy.strategyversion.dto.StrategyVersionResponse;
import com.lifeos.strategy.strategyversion.dto.StrategyVersionResponse.PhaseResponse;
import com.lifeos.strategy.strategyversion.entity.Phase;
import com.lifeos.strategy.strategyversion.entity.StrategyVersion;
import com.lifeos.strategy.strategyversion.event.StrategyRevisedV1;
import com.lifeos.strategy.strategyversion.event.StrategyRevisedV1.PhaseV1;
import org.springframework.stereotype.Component;

@Component
public class StrategyVersionMapper {

    public StrategyVersionResponse toResponse(StrategyVersion version) {
        return new StrategyVersionResponse(version.getId(), version.getGoalId(), version.getVersionNo(),
                version.getSummary(), version.getChangeReason(), version.getSourceProposalId(), version.isCurrent(),
                version.getCreatedAt(), version.getPhases().stream().map(this::toResponse).toList(),
                version.getEffortAllocation());
    }

    /** @param previousVersionNo the version this one replaced, {@code null} for the goal's first strategy */
    public StrategyRevisedV1 toRevisedEvent(StrategyVersion version, Integer previousVersionNo) {
        return new StrategyRevisedV1(version.getGoalId(), version.getId(), version.getVersionNo(),
                previousVersionNo, version.getSummary(), version.getChangeReason(), version.getSourceProposalId(),
                version.getPhases().stream().map(this::toEvent).toList(), version.getEffortAllocation());
    }

    private PhaseResponse toResponse(Phase phase) {
        return new PhaseResponse(phase.getPhaseKey(), phase.getOrdinal(), phase.getTitle(), phase.getStartsOn(),
                phase.getEndsOn(), phase.getExitCriteria(), phase.getStatus());
    }

    private PhaseV1 toEvent(Phase phase) {
        return new PhaseV1(phase.getPhaseKey(), phase.getOrdinal(), phase.getTitle(), phase.getStartsOn(),
                phase.getEndsOn(), phase.getExitCriteria(), phase.getStatus());
    }
}
