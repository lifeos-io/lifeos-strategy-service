package com.lifeos.strategy.decision.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Payload of {@code DecisionReopened} v1 (lifeos-contracts: schemas/strategy/DecisionReopened.v1.schema.json). */
public record DecisionReopenedV1(
        UUID decisionId,
        UUID reopenedFromId,
        String title,
        String reason,
        List<UUID> falsifiedAssumptionIds,
        Instant reopenedAt) {

    public static final String TYPE = "DecisionReopened";
    public static final int VERSION = 1;
}
