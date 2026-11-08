package com.lifeos.strategy.action.repository;

import com.lifeos.strategy.action.entity.Action;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActionRepository extends JpaRepository<Action, UUID> {

    @EntityGraph(attributePaths = "goalIds")
    Optional<Action> findByIdAndWeeklyPlanId(UUID id, UUID weeklyPlanId);

    /** In the order the user arranged them. */
    @EntityGraph(attributePaths = "goalIds")
    List<Action> findByWeeklyPlanIdOrderBySortOrderAscCreatedAtAsc(UUID weeklyPlanId);
}
