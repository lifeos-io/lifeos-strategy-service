package com.lifeos.strategy.knowledge.dto;

import com.lifeos.strategy.knowledge.entity.KnowledgeKind;
import com.lifeos.strategy.knowledge.entity.KnowledgeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/strategy/goals/{goalId}/knowledge-items}.
 *
 * @param status   defaults to UNVERIFIED
 * @param external about the outside world (market, companies) rather than about the user; defaults to false
 */
public record CreateKnowledgeItemRequest(
        @NotBlank @Size(max = 500) String statement,
        @NotNull KnowledgeKind kind,
        KnowledgeStatus status,
        Boolean external,
        @Size(max = 500) String source,
        @Size(max = 1000) String howToVerify) {
}
