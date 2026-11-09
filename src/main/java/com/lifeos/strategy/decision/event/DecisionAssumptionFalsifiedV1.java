package com.lifeos.strategy.decision.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload of {@code DecisionAssumptionFalsified} v1
 * (lifeos-contracts: schemas/strategy/DecisionAssumptionFalsified.v1.schema.json).
 */
public record DecisionAssumptionFalsifiedV1(
        UUID decisionId,
        UUID goalId,
        String title,
        UUID knowledgeItemId,
        String statement,
        Instant falsifiedAt) {

    public static final String TYPE = "DecisionAssumptionFalsified";
    public static final int VERSION = 1;
}
