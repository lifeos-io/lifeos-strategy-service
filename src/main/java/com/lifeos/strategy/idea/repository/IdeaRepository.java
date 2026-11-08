package com.lifeos.strategy.idea.repository;

import com.lifeos.strategy.idea.entity.Idea;
import com.lifeos.strategy.idea.entity.IdeaStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IdeaRepository extends JpaRepository<Idea, UUID> {

    Optional<Idea> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndStatus(UUID userId, IdeaStatus status);

    /** Newest first; a {@code null} filter matches everything. */
    @Query("""
            SELECT i FROM Idea i
            WHERE i.userId = :userId
              AND (:status IS NULL OR i.status = :status)
              AND (:goalId IS NULL OR i.goalId = :goalId)
            ORDER BY i.createdAt DESC
            """)
    List<Idea> search(@Param("userId") UUID userId, @Param("status") IdeaStatus status,
            @Param("goalId") UUID goalId);
}
