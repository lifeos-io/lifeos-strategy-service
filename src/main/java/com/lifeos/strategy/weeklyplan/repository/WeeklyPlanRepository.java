package com.lifeos.strategy.weeklyplan.repository;

import com.lifeos.strategy.weeklyplan.entity.WeeklyPlan;
import com.lifeos.strategy.weeklyplan.entity.WeeklyPlanStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyPlanRepository extends JpaRepository<WeeklyPlan, UUID> {

    @EntityGraph(attributePaths = "allocations")
    Optional<WeeklyPlan> findByUserIdAndIsoWeek(UUID userId, String isoWeek);

    boolean existsByUserIdAndIsoWeek(UUID userId, String isoWeek);

    Optional<WeeklyPlan> findByUserIdAndStatus(UUID userId, WeeklyPlanStatus status);

    /** Newest week first. */
    @EntityGraph(attributePaths = "allocations")
    List<WeeklyPlan> findByUserIdOrderByWeekStartDesc(UUID userId);

    @EntityGraph(attributePaths = "allocations")
    List<WeeklyPlan> findByUserIdAndStatusOrderByWeekStartDesc(UUID userId, WeeklyPlanStatus status);
}
