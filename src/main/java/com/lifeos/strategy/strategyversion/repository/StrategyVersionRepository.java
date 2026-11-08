package com.lifeos.strategy.strategyversion.repository;

import com.lifeos.strategy.strategyversion.entity.StrategyVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StrategyVersionRepository extends JpaRepository<StrategyVersion, UUID> {

    @EntityGraph(attributePaths = "phases")
    Optional<StrategyVersion> findByGoalIdAndCurrentTrue(UUID goalId);

    @EntityGraph(attributePaths = "phases")
    Optional<StrategyVersion> findByGoalIdAndVersionNo(UUID goalId, int versionNo);

    /** Newest first. */
    @EntityGraph(attributePaths = "phases")
    List<StrategyVersion> findByGoalIdOrderByVersionNoDesc(UUID goalId);
}
