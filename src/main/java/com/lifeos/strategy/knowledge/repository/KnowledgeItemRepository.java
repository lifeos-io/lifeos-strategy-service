package com.lifeos.strategy.knowledge.repository;

import com.lifeos.strategy.knowledge.entity.KnowledgeItem;
import com.lifeos.strategy.knowledge.entity.KnowledgeKind;
import com.lifeos.strategy.knowledge.entity.KnowledgeStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeItemRepository extends JpaRepository<KnowledgeItem, UUID> {

    /** Scoped to the goal and its owner: an item of another goal or user is reported as missing. */
    Optional<KnowledgeItem> findByIdAndGoalIdAndUserId(UUID id, UUID goalId, UUID userId);

    /** Oldest first; a {@code null} filter matches everything. */
    @Query("""
            SELECT k FROM KnowledgeItem k
            WHERE k.goalId = :goalId
              AND (:kind IS NULL OR k.kind = :kind)
              AND (:status IS NULL OR k.status = :status)
            ORDER BY k.createdAt, k.id
            """)
    List<KnowledgeItem> search(@Param("goalId") UUID goalId, @Param("kind") KnowledgeKind kind,
            @Param("status") KnowledgeStatus status);
}
