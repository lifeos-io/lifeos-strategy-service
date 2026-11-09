package com.lifeos.strategy.decision.repository;

import com.lifeos.strategy.decision.entity.Decision;
import com.lifeos.strategy.decision.entity.DecisionStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /** The user's decisions in {@code status} that rest on the knowledge item. */
    @Query("""
            SELECT d FROM Decision d JOIN d.assumptionIds a
            WHERE d.userId = :userId AND d.status = :status AND a = :knowledgeItemId
            """)
    List<Decision> findRestingOn(@Param("userId") UUID userId, @Param("knowledgeItemId") UUID knowledgeItemId,
            @Param("status") DecisionStatus status);

    /** The draft (or later decision) reopened from {@code decisionId}, if any; at most one (unique). */
    Optional<Decision> findByReopenedFromId(UUID decisionId);

    List<Decision> findByReopenedFromIdIn(Collection<UUID> decisionIds);

    /**
     * The decision with a row lock until the transaction ends, so two reopenings of the same decision run one
     * after another and the second sees the first.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Decision d WHERE d.id = :id AND d.userId = :userId")
    Optional<Decision> findForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);
}
