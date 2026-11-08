package com.lifeos.strategy.decision.mapper;

import com.lifeos.strategy.decision.dto.DecisionResponse;
import com.lifeos.strategy.decision.dto.DecisionResponse.AlternativeResponse;
import com.lifeos.strategy.decision.dto.DecisionResponse.TriggerResponse;
import com.lifeos.strategy.decision.entity.Decision;
import com.lifeos.strategy.decision.event.DecisionClosedV1;
import com.lifeos.strategy.decision.event.DecisionRecordedV1;
import com.lifeos.strategy.decision.event.DecisionRecordedV1.AlternativeV1;
import com.lifeos.strategy.decision.event.DecisionRecordedV1.TriggerV1;
import org.springframework.stereotype.Component;

@Component
public class DecisionMapper {

    public DecisionResponse toResponse(Decision decision) {
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
                decision.getVersion(), decision.getCreatedAt(), decision.getUpdatedAt());
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
}
