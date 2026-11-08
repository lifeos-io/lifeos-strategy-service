package com.lifeos.strategy.knowledge.dto;

import com.lifeos.strategy.knowledge.entity.KnowledgeKind;
import com.lifeos.strategy.knowledge.entity.KnowledgeStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /api/strategy/goals/{goalId}/knowledge-items/{itemId}}: only the fields present change.
 *
 * @param version the version the client last read (409 {@code CONCURRENT_MODIFICATION} when it is stale)
 * @param status  VERIFIED or FALSIFIED stamps {@code verifiedAt}; back to UNVERIFIED or PARTIAL clears it
 */
public record UpdateKnowledgeItemRequest(
        @NotNull Integer version,
        @Size(max = 500) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String statement,
        KnowledgeKind kind,
        KnowledgeStatus status,
        Boolean external,
        @Size(max = 500) String source,
        @Size(max = 1000) String howToVerify) {
}
