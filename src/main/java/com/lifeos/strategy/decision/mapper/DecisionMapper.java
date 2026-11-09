package com.lifeos.strategy.decision.mapper;

import com.lifeos.strategy.decision.dto.DecisionResponse;
import com.lifeos.strategy.decision.dto.DecisionResponse.AlternativeResponse;
import com.lifeos.strategy.decision.dto.DecisionResponse.TriggerResponse;
import com.lifeos.strategy.decision.entity.Decision;
import com.lifeos.strategy.decision.event.DecisionAssumptionFalsifiedV1;
import com.lifeos.strategy.decision.event.DecisionClosedV1;
import com.lifeos.strategy.decision.event.DecisionRecordedV1;
import com.lifeos.strategy.decision.event.DecisionRecordedV1.AlternativeV1;
import com.lifeos.strategy.decision.event.DecisionRecordedV1.TriggerV1;
import com.lifeos.strategy.decision.event.DecisionReopenedV1;
import com.lifeos.strategy.decision.event.DecisionSupersededV1;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DecisionMapper {

    /**
     * @param falsifiedAssumptionIds the decision's assumptions that are FALSIFIED now
     * @param reopenedAsId           the decision reopened from this one, if any
     */
    public DecisionResponse toResponse(Decision decision, List<UUID> falsifiedAssumptionIds, UUID reopenedAsId) {
        return new DecisionResponse(decision.getId(), decision.getGoalId(), decision.getTitle(),
                decision.getContext(), decision.getStatus(),
                decision.getAlternatives().stream()
                        .map(a -> new AlternativeResponse(a.getLabel(), a.getDescription(), a.isChosen(),
                                a.getRejectionReason()))
                        .toList(),
                decision.getAssumptionIds().stream().sorted().toList(),
                decision.getReopenTriggers().stream()
                        .map(t -> new TriggerResponse(t.getKind(), t.getDescription(), t.getEventType(),
                                t.getFiresOn(), t.getFiredAt()))
                        .toList(),
                decision.getExpectedOutcome(), decision.getDecidedAt(), decision.getReviewOn(),
                decision.getOutcomeNote(), decision.getWouldDecideAgain(), decision.getClosedAt(),
                decision.getReopenedFromId(), decision.getReopenReason(), reopenedAsId, decision.getSupersededAt(),
                falsifiedAssumptionIds, decision.getVersion(), decision.getCreatedAt(), decision.getUpdatedAt());
    }

    public DecisionRecordedV1 toRecordedEvent(Decision decision) {
        return new DecisionRecordedV1(decision.getId(), decision.getGoalId(), decision.getTitle(),
                decision.getContext(),
                decision.getAlternatives().stream()
                        .map(a -> new AlternativeV1(a.getLabel(), a.getDescription(), a.isChosen(),
                                a.getRejectionReason()))
                        .toList(),
                decision.getExpectedOutcome(), decision.getDecidedAt(), decision.getReviewOn(),
                decision.getAssumptionIds().stream().sorted().toList(),
                decision.getReopenTriggers().stream()
                        .map(t -> new TriggerV1(t.getKind(), t.getDescription(), t.getEventType(), t.getFiresOn()))
                        .toList());
    }

    public DecisionClosedV1 toClosedEvent(Decision decision) {
        return new DecisionClosedV1(decision.getId(), decision.getGoalId(), decision.getTitle(),
                decision.getExpectedOutcome(), decision.getOutcomeNote(), decision.getWouldDecideAgain(),
                decision.getDecidedAt(), decision.getClosedAt());
    }

    public DecisionReopenedV1 toReopenedEvent(Decision draft, List<UUID> falsifiedAssumptionIds) {
        return new DecisionReopenedV1(draft.getId(), draft.getReopenedFromId(), draft.getTitle(),
                draft.getReopenReason(), falsifiedAssumptionIds, draft.getCreatedAt());
    }

    public DecisionSupersededV1 toSupersededEvent(Decision original, UUID supersededById) {
        return new DecisionSupersededV1(original.getId(), supersededById, original.getSupersededAt());
    }

    public DecisionAssumptionFalsifiedV1 toAssumptionFalsifiedEvent(Decision decision, UUID knowledgeItemId,
            String statement, Instant falsifiedAt) {
        return new DecisionAssumptionFalsifiedV1(decision.getId(), decision.getGoalId(), decision.getTitle(),
                knowledgeItemId, statement, falsifiedAt);
    }
}
