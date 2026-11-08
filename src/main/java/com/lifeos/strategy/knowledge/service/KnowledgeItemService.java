package com.lifeos.strategy.knowledge.service;

import com.lifeos.strategy.knowledge.dto.CreateKnowledgeItemRequest;
import com.lifeos.strategy.knowledge.dto.KnowledgeItemResponse;
import com.lifeos.strategy.knowledge.dto.UpdateKnowledgeItemRequest;
import com.lifeos.strategy.knowledge.entity.KnowledgeKind;
import com.lifeos.strategy.knowledge.entity.KnowledgeStatus;
import java.util.List;
import java.util.UUID;

/**
 * A goal's knowledge map. Every creation and every real change publishes {@code KnowledgeItemChanged} with the
 * item's full state. Items of a closed (achieved or abandoned) goal are read-only.
 */
public interface KnowledgeItemService {

    KnowledgeItemResponse create(UUID userId, UUID goalId, CreateKnowledgeItemRequest request);

    /** Oldest first; {@code null} filters match everything. */
    List<KnowledgeItemResponse> list(UUID userId, UUID goalId, KnowledgeKind kind, KnowledgeStatus status);

    KnowledgeItemResponse get(UUID userId, UUID goalId, UUID itemId);

    KnowledgeItemResponse update(UUID userId, UUID goalId, UUID itemId, UpdateKnowledgeItemRequest request);
}
