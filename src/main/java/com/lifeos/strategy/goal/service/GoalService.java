package com.lifeos.strategy.goal.service;

import com.lifeos.strategy.goal.dto.CreateGoalRequest;
import com.lifeos.strategy.goal.dto.GoalResponse;
import com.lifeos.strategy.goal.dto.ReorderGoalsRequest;
import com.lifeos.strategy.goal.dto.UpdateGoalRequest;
import com.lifeos.strategy.goal.entity.GoalStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The user's goals. Every creation and every real change publishes {@code GoalChanged} with the goal's full
 * state, so Kule and Tetikleyici keep their own copies up to date.
 */
public interface GoalService {

    GoalResponse create(UUID userId, CreateGoalRequest request);

    /** The user's goals by priority; {@code statuses} empty means all of them. */
    List<GoalResponse> list(UUID userId, Set<GoalStatus> statuses);

    GoalResponse get(UUID userId, UUID goalId);

    GoalResponse update(UUID userId, UUID goalId, UpdateGoalRequest request);

    /** Re-numbers the open goals 1..n in the given order; returns them in that order. */
    List<GoalResponse> reorder(UUID userId, ReorderGoalsRequest request);
}
