package com.lifeos.strategy.goal.repository;

import com.lifeos.strategy.goal.entity.Goal;
import com.lifeos.strategy.goal.entity.GoalStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    /** Another user's goal is reported as missing, never as forbidden. */
    Optional<Goal> findByIdAndUserId(UUID id, UUID userId);

    /**
     * The goal with a row lock ({@code SELECT ... FOR UPDATE}) until the transaction ends: changes that depend on
     * the goal's current state (e.g. a new strategy version) run one after another per goal.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Goal g WHERE g.id = :id AND g.userId = :userId")
    Optional<Goal> findForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    List<Goal> findByUserIdOrderByPriorityAscCreatedAtAsc(UUID userId);

    List<Goal> findByUserIdAndStatusInOrderByPriorityAscCreatedAtAsc(UUID userId, Collection<GoalStatus> statuses);

    /** Highest priority number among the user's open goals, 0 when there is none. */
    @Query("""
            SELECT COALESCE(MAX(g.priority), 0) FROM Goal g
            WHERE g.userId = :userId AND g.status IN :statuses
            """)
    int findMaxPriority(@Param("userId") UUID userId, @Param("statuses") Collection<GoalStatus> statuses);
}
