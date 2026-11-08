package com.lifeos.strategy.strategyversion.repository;

import com.lifeos.strategy.strategyversion.entity.StrategyVersion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StrategyVersionRepository extends JpaRepository<StrategyVersion, UUID> {

    @EntityGraph(attributePaths = "phases")
    Optional<StrategyVersion> findByGoalIdAndCurrentTrue(UUID goalId);

    @EntityGraph(attributePaths = "phases")
    Optional<StrategyVersion> findByGoalIdAndVersionNo(UUID goalId, int versionNo);

    /** The current versions of several goals, without their phases. */
    List<StrategyVersion> findByGoalIdInAndCurrentTrue(Collection<UUID> goalIds);

    /** Whether a phase with this key belongs to the current strategy of one of the goals. */
    @Query("""
            SELECT COUNT(p) > 0 FROM Phase p
            WHERE p.phaseKey = :phaseKey AND p.strategyVersion.current = true AND p.strategyVersion.goalId IN :goalIds
            """)
    boolean existsCurrentPhase(@Param("phaseKey") UUID phaseKey, @Param("goalIds") Collection<UUID> goalIds);

    /** Newest first. */
    @EntityGraph(attributePaths = "phases")
    List<StrategyVersion> findByGoalIdOrderByVersionNoDesc(UUID goalId);
}
