package com.lifeos.strategy.decision.event;

import java.time.Instant;
import java.util.UUID;

/** Payload of {@code DecisionSuperseded} v1 (lifeos-contracts: schemas/strategy/DecisionSuperseded.v1.schema.json). */
public record DecisionSupersededV1(UUID decisionId, UUID supersededById, Instant supersededAt) {

    public static final String TYPE = "DecisionSuperseded";
    public static final int VERSION = 1;
}
