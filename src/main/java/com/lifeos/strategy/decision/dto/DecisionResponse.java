package com.lifeos.strategy.decision.dto;

import com.lifeos.strategy.decision.entity.DecisionStatus;
import com.lifeos.strategy.decision.entity.TriggerKind;
import com.lifeos.strategy.decision.entity.WouldDecideAgain;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DecisionResponse(
        UUID id,
        UUID goalId,
        String title,
        String context,
        DecisionStatus status,
        List<AlternativeResponse> alternatives,
        List<UUID> assumptionIds,
        List<TriggerResponse> reopenTriggers,
        String expectedOutcome,
        Instant decidedAt,
        LocalDate reviewOn,
        String outcomeNote,
        WouldDecideAgain wouldDecideAgain,
        Instant closedAt,
        int version,
        Instant createdAt,
        Instant updatedAt) {

    public record AlternativeResponse(String label, String description, boolean chosen, String rejectionReason) {
    }

    public record TriggerResponse(TriggerKind kind, String description, String eventType, LocalDate firesOn,
            Instant firedAt) {
    }
}
