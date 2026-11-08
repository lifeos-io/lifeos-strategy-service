package com.lifeos.strategy.knowledge.dto;

import com.lifeos.strategy.knowledge.entity.KnowledgeKind;
import com.lifeos.strategy.knowledge.entity.KnowledgeStatus;
import java.time.Instant;
import java.util.UUID;

public record KnowledgeItemResponse(
        UUID id,
        UUID goalId,
        String statement,
        KnowledgeKind kind,
        KnowledgeStatus status,
        boolean external,
        String source,
        String howToVerify,
        Instant verifiedAt,
        int version,
        Instant createdAt,
        Instant updatedAt) {
}
