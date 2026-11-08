package com.lifeos.strategy.goal.service.impl;

import com.lifeos.base.error.CommonErrorCode;
import com.lifeos.base.error.LifeOsException;
import com.lifeos.base.messaging.outbox.Outbox;
import com.lifeos.base.messaging.outbox.OutboxMessage;
import com.lifeos.strategy.common.error.StrategyErrorCode;
import com.lifeos.strategy.goal.dto.CreateGoalRequest;
import com.lifeos.strategy.goal.dto.GoalResponse;
import com.lifeos.strategy.goal.dto.ReorderGoalsRequest;
import com.lifeos.strategy.goal.dto.UpdateGoalRequest;
import com.lifeos.strategy.goal.entity.Goal;
import com.lifeos.strategy.goal.entity.GoalStatus;
import com.lifeos.strategy.goal.event.GoalChangedV1;
import com.lifeos.strategy.goal.mapper.GoalMapper;
import com.lifeos.strategy.goal.repository.GoalRepository;
import com.lifeos.strategy.goal.service.GoalService;
import com.lifeos.strategy.profile.service.ProfileService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changes are flushed before the event is written, so {@code GoalChanged.version} is the version the change
 * produced. Events go through the outbox in the same transaction as the change.
 */
@Service
public class GoalServiceImpl implements GoalService {

    private final GoalRepository repository;
    private final GoalMapper goalMapper;
    private final ProfileService profileService;
    private final Outbox outbox;
    private final Clock clock;

    public GoalServiceImpl(GoalRepository repository, GoalMapper goalMapper, ProfileService profileService,
            Outbox outbox, Clock clock) {
        this.repository = repository;
        this.goalMapper = goalMapper;
        this.profileService = profileService;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    @Transactional
    public GoalResponse create(UUID userId, CreateGoalRequest request) {
        // A goal belongs to a profile (foreign key); a brand-new user gets one here.
        profileService.getOrCreate(userId);
        int priority = repository.findMaxPriority(userId, GoalStatus.OPEN) + 1;
        Goal goal = Goal.create(userId, request.title().strip(), request.domain(), strip(request.whyItMatters()),
                strip(request.successDefinition()), request.windowStart(), request.windowEnd(), priority,
                clock.instant());
        repository.saveAndFlush(goal);
        publishChanged(goal);
        return goalMapper.toResponse(goal);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GoalResponse> list(UUID userId, Set<GoalStatus> statuses) {
        List<Goal> goals = statuses.isEmpty()
                ? repository.findByUserIdOrderByPriorityAscCreatedAtAsc(userId)
                : repository.findByUserIdAndStatusInOrderByPriorityAscCreatedAtAsc(userId, statuses);
        return goals.stream().map(goalMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GoalResponse get(UUID userId, UUID goalId) {
        return goalMapper.toResponse(load(userId, goalId));
    }

    @Override
    @Transactional
    public GoalResponse update(UUID userId, UUID goalId, UpdateGoalRequest request) {
        Goal goal = load(userId, goalId);
        if (goal.getVersion() != request.version()) {
            throw new LifeOsException(CommonErrorCode.CONCURRENT_MODIFICATION,
                    "The goal was changed in the meantime. Reload it and try again.",
                    Map.of("currentVersion", goal.getVersion()));
        }
        Instant now = clock.instant();
        // Status last: a request may edit an open goal and close it in one go.
        boolean changed = goal.update(strip(request.title()), request.domain(), strip(request.whyItMatters()),
                strip(request.successDefinition()), request.windowStart(), request.windowEnd(), now);
        if (request.status() != null) {
            changed |= goal.changeStatus(request.status(), now);
        }
        if (changed) {
            repository.saveAndFlush(goal);
            publishChanged(goal);
        }
        return goalMapper.toResponse(goal);
    }

    @Override
    @Transactional
    public List<GoalResponse> reorder(UUID userId, ReorderGoalsRequest request) {
        List<Goal> open = repository.findByUserIdAndStatusInOrderByPriorityAscCreatedAtAsc(userId, GoalStatus.OPEN);
        Map<UUID, Goal> byId = open.stream().collect(Collectors.toMap(Goal::getId, Function.identity()));
        List<UUID> order = request.goalIds();
        if (order.size() != byId.size() || !new HashSet<>(order).equals(byId.keySet())) {
            throw new LifeOsException(StrategyErrorCode.STRATEGY_GOAL_ORDER_MISMATCH,
                    "List every active and paused goal exactly once.", Map.of("openGoals", byId.size()));
        }
        Instant now = clock.instant();
        List<Goal> ordered = new ArrayList<>(order.size());
        List<Goal> changed = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            Goal goal = byId.get(order.get(i));
            if (goal.reprioritize(i + 1, now)) {
                changed.add(goal);
            }
            ordered.add(goal);
        }
        repository.saveAllAndFlush(changed);
        changed.forEach(this::publishChanged);
        return ordered.stream().map(goalMapper::toResponse).toList();
    }

    private Goal load(UUID userId, UUID goalId) {
        return repository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new LifeOsException(CommonErrorCode.NOT_FOUND, "Goal not found."));
    }

    private void publishChanged(Goal goal) {
        outbox.append(new OutboxMessage("Goal", goal.getId(), goal.getUserId(),
                GoalChangedV1.TYPE, GoalChangedV1.VERSION, goalMapper.toChangedEvent(goal)));
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
