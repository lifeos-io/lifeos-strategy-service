package com.lifeos.strategy.evidence.repository;

import com.lifeos.strategy.evidence.entity.Evidence;
import com.lifeos.strategy.evidence.entity.EvidenceLevel;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {

    Optional<Evidence> findByIdAndUserId(UUID id, UUID userId);

    /** Evidence that occurred between {@code from} and {@code to}, both inclusive (e.g. one week). */
    long countByUserIdAndOccurredOnBetween(UUID userId, LocalDate from, LocalDate to);

    /** Most recent first; a {@code null} filter matches everything. */
    @Query("""
            SELECT DISTINCT e FROM Evidence e LEFT JOIN e.goalIds g
            WHERE e.userId = :userId
              AND (:goalId IS NULL OR g = :goalId)
              AND (:level IS NULL OR e.level = :level)
            ORDER BY e.occurredOn DESC, e.createdAt DESC
            """)
    List<Evidence> search(@Param("userId") UUID userId, @Param("goalId") UUID goalId,
            @Param("level") EvidenceLevel level);
}
