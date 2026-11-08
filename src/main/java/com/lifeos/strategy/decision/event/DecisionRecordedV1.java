package com.lifeos.strategy.decision.event;

import com.lifeos.strategy.decision.entity.TriggerKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Payload of {@code DecisionRecorded} v1 (lifeos-contracts: schemas/strategy/DecisionRecorded.v1.schema.json). */
public record DecisionRecordedV1(
        UUID decisionId,
        UUID goalId,
        String title,
        String context,
        List<AlternativeV1> alternatives,
        String expectedOutcome,
        Instant decidedAt,
        LocalDate reviewOn,
        List<UUID> assumptionIds,
        List<TriggerV1> reopenTriggers) {

    public static final String TYPE = "DecisionRecorded";
    public static final int VERSION = 1;

    public record AlternativeV1(String label, String description, boolean chosen, String rejectionReason) {
    }

    public record TriggerV1(TriggerKind kind, String description, String eventType, LocalDate firesOn) {
    }
}
