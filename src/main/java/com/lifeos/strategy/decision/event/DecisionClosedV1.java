package com.lifeos.strategy.decision.event;

import com.lifeos.strategy.decision.entity.WouldDecideAgain;
import java.time.Instant;
import java.util.UUID;

/** Payload of {@code DecisionClosed} v1 (lifeos-contracts: schemas/strategy/DecisionClosed.v1.schema.json). */
public record DecisionClosedV1(
        UUID decisionId,
        UUID goalId,
        String title,
        String expectedOutcome,
        String outcomeNote,
        WouldDecideAgain wouldDecideAgain,
        Instant decidedAt,
        Instant closedAt) {

    public static final String TYPE = "DecisionClosed";
    public static final int VERSION = 1;
}
