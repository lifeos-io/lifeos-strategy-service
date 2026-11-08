package com.lifeos.strategy.goal.repository;

import com.lifeos.strategy.goal.entity.Goal;
import com.lifeos.strategy.goal.entity.GoalStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    /** Another user's goal is reported as missing, never as forbidden. */
    Optional<Goal> findByIdAndUserId(UUID id, UUID userId);

    List<Goal> findByUserIdOrderByPriorityAscCreatedAtAsc(UUID userId);

    List<Goal> findByUserIdAndStatusInOrderByPriorityAscCreatedAtAsc(UUID userId, Collection<GoalStatus> statuses);

    /** Highest priority number among the user's open goals, 0 when there is none. */
    @Query("""
            SELECT COALESCE(MAX(g.priority), 0) FROM Goal g
            WHERE g.userId = :userId AND g.status IN :statuses
            """)
    int findMaxPriority(@Param("userId") UUID userId, @Param("statuses") Collection<GoalStatus> statuses);
}
