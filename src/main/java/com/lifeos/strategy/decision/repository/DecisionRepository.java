package com.lifeos.strategy.decision.repository;

import com.lifeos.strategy.decision.entity.Decision;
import com.lifeos.strategy.decision.entity.DecisionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {

    Optional<Decision> findByIdAndUserId(UUID id, UUID userId);

    /** Newest first; a {@code null} filter matches everything. */
    @Query("""
            SELECT d FROM Decision d
            WHERE d.userId = :userId
              AND (:status IS NULL OR d.status = :status)
              AND (:goalId IS NULL OR d.goalId = :goalId)
            ORDER BY d.createdAt DESC
            """)
    List<Decision> search(@Param("userId") UUID userId, @Param("status") DecisionStatus status,
            @Param("goalId") UUID goalId);
}
