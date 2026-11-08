package com.lifeos.strategy.proposal.repository;

import com.lifeos.strategy.proposal.entity.ChangeProposal;
import com.lifeos.strategy.proposal.entity.ProposalStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChangeProposalRepository extends JpaRepository<ChangeProposal, UUID> {

    @EntityGraph(attributePaths = "sources")
    Optional<ChangeProposal> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByRecommendationId(UUID recommendationId);

    /** The goal's proposal that waits for the user, if any (at most one, unique index). */
    Optional<ChangeProposal> findByGoalIdAndStatus(UUID goalId, ProposalStatus status);

    /** Newest first; a {@code null} filter matches everything. */
    @EntityGraph(attributePaths = "sources")
    @Query("""
            SELECT p FROM ChangeProposal p
            WHERE p.userId = :userId
              AND (:status IS NULL OR p.status = :status)
              AND (:goalId IS NULL OR p.goalId = :goalId)
            ORDER BY p.createdAt DESC
            """)
    List<ChangeProposal> search(@Param("userId") UUID userId, @Param("status") ProposalStatus status,
            @Param("goalId") UUID goalId);
}
