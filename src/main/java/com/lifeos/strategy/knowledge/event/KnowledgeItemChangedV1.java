package com.lifeos.strategy.knowledge.event;

import com.lifeos.strategy.knowledge.entity.KnowledgeKind;
import com.lifeos.strategy.knowledge.entity.KnowledgeStatus;
import java.time.Instant;
import java.util.UUID;

/** Payload of {@code KnowledgeItemChanged} v1 (lifeos-contracts: schemas/strategy/KnowledgeItemChanged.v1.schema.json). */
public record KnowledgeItemChangedV1(
        UUID knowledgeItemId,
        UUID goalId,
        String statement,
        KnowledgeKind kind,
        KnowledgeStatus status,
        boolean external,
        String source,
        String howToVerify,
        Instant verifiedAt,
        int version) {

    public static final String TYPE = "KnowledgeItemChanged";
    public static final int VERSION = 1;
}
