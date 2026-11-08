package com.lifeos.strategy.weeklyplan.service.impl;

import com.lifeos.base.error.CommonErrorCode;
import com.lifeos.base.error.LifeOsException;
import com.lifeos.base.messaging.outbox.Outbox;
import com.lifeos.base.messaging.outbox.OutboxMessage;
import com.lifeos.strategy.action.entity.Action;
import com.lifeos.strategy.action.repository.ActionRepository;
import com.lifeos.strategy.common.error.FieldErrors;
import com.lifeos.strategy.common.error.StrategyErrorCode;
import com.lifeos.strategy.common.time.IsoWeeks;
import com.lifeos.strategy.goal.entity.Goal;
import com.lifeos.strategy.goal.entity.GoalStatus;
import com.lifeos.strategy.goal.repository.GoalRepository;
import com.lifeos.strategy.profile.dto.ProfileResponse;
import com.lifeos.strategy.profile.service.ProfileService;
import com.lifeos.strategy.strategyversion.entity.StrategyVersion;
import com.lifeos.strategy.strategyversion.repository.StrategyVersionRepository;
import com.lifeos.strategy.weeklyplan.dto.AllocationRequest;
import com.lifeos.strategy.weeklyplan.dto.CreateWeeklyPlanRequest;
import com.lifeos.strategy.weeklyplan.dto.UpdateWeeklyPlanRequest;
import com.lifeos.strategy.weeklyplan.dto.WeeklyPlanResponse;
import com.lifeos.strategy.weeklyplan.entity.GoalAllocation;
import com.lifeos.strategy.weeklyplan.entity.WeeklyPlan;
import com.lifeos.strategy.weeklyplan.entity.WeeklyPlanStatus;
import com.lifeos.strategy.weeklyplan.event.CapacityChangedV1;
import com.lifeos.strategy.weeklyplan.event.WeeklyPlanActivatedV1;
import com.lifeos.strategy.weeklyplan.mapper.WeeklyPlanMapper;
import com.lifeos.strategy.weeklyplan.repository.WeeklyPlanRepository;
import com.lifeos.strategy.weeklyplan.service.WeeklyPlanService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeeklyPlanServiceImpl implements WeeklyPlanService {

    private final WeeklyPlanRepository repository;
    private final GoalRepository goalRepository;
    private final StrategyVersionRepository strategyVersionRepository;
    private final ActionRepository actionRepository;
    private final ProfileService profileService;
    private final WeeklyPlanMapper weeklyPlanMapper;
    private final Outbox outbox;
    private final Clock clock;

    public WeeklyPlanServiceImpl(WeeklyPlanRepository repository, GoalRepository goalRepository,
            StrategyVersionRepository strategyVersionRepository, ActionRepository actionRepository,
            ProfileService profileService, WeeklyPlanMapper weeklyPlanMapper, Outbox outbox, Clock clock) {
        this.repository = repository;
        this.goalRepository = goalRepository;
        this.strategyVersionRepository = strategyVersionRepository;
        this.actionRepository = actionRepository;
        this.profileService = profileService;
        this.weeklyPlanMapper = weeklyPlanMapper;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    @Transactional
    public WeeklyPlanResponse create(UUID userId, CreateWeeklyPlanRequest request) {
        LocalDate weekStart = IsoWeeks.monday(request.isoWeek())
                .orElseThrow(() -> FieldErrors.invalid("isoWeek", "must be an ISO week such as 2026-W46"));
        // The plan belongs to a profile (foreign key) and takes its default capacity from it.
        ProfileResponse profile = profileService.getOrCreate(userId);
        if (repository.existsByUserIdAndIsoWeek(userId, request.isoWeek())) {
            throw new LifeOsException(StrategyErrorCode.STRATEGY_PLAN_EXISTS,
                    "There is already a plan for " + request.isoWeek() + ".");
        }
        int capacity = request.capacityMinutes() != null
                ? request.capacityMinutes() : profile.defaultWeeklyMinutes();
        List<GoalAllocation> allocations = toAllocations(userId, request.allocations(), false);
        requireWithinCapacity(capacity, allocations);
        WeeklyPlan plan = WeeklyPlan.create(userId, request.isoWeek(), weekStart, capacity,
                strip(request.headline()), allocations, clock.instant());
        repository.saveAndFlush(plan);
        return weeklyPlanMapper.toResponse(plan);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WeeklyPlanResponse> list(UUID userId, WeeklyPlanStatus status) {
        List<WeeklyPlan> plans = status == null
                ? repository.findByUserIdOrderByWeekStartDesc(userId)
                : repository.findByUserIdAndStatusOrderByWeekStartDesc(userId, status);
        return plans.stream().map(weeklyPlanMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WeeklyPlanResponse get(UUID userId, String isoWeek) {
        return weeklyPlanMapper.toResponse(load(userId, isoWeek));
    }

    @Override
    @Transactional
    public WeeklyPlanResponse update(UUID userId, String isoWeek, UpdateWeeklyPlanRequest request) {
        WeeklyPlan plan = load(userId, isoWeek);
        if (plan.getVersion() != request.version()) {
            throw new LifeOsException(CommonErrorCode.CONCURRENT_MODIFICATION,
                    "The plan was changed in the meantime. Reload it and try again.",
                    Map.of("currentVersion", plan.getVersion()));
        }
        plan.requireEditable();
        // A committed week records the strategy each goal is planned with, also for goals added later.
        List<GoalAllocation> allocations = request.allocations() == null ? null
                : toAllocations(userId, request.allocations(), plan.getStatus() == WeeklyPlanStatus.ACTIVE);
        int capacity = request.capacityMinutes() != null ? request.capacityMinutes() : plan.getCapacityMinutes();
        requireWithinCapacity(capacity, allocations != null ? allocations : plan.getAllocations());
        List<Action> actions = actionRepository.findByWeeklyPlanIdOrderBySortOrderAscCreatedAtAsc(plan.getId());
        requireActionsFit(capacity, allocations, actions);

        int previousCapacity = plan.getCapacityMinutes();
        if (plan.update(request.capacityMinutes(), strip(request.headline()), allocations, clock.instant())) {
            repository.saveAndFlush(plan);
            if (plan.getStatus() == WeeklyPlanStatus.ACTIVE && plan.getCapacityMinutes() != previousCapacity) {
                outbox.append(new OutboxMessage("WeeklyPlan", plan.getId(), userId, CapacityChangedV1.TYPE,
                        CapacityChangedV1.VERSION, weeklyPlanMapper.toCapacityChangedEvent(plan, previousCapacity)));
            }
        }
        return weeklyPlanMapper.toResponse(plan);
    }

    @Override
    @Transactional
    public WeeklyPlanResponse activate(UUID userId, String isoWeek) {
        WeeklyPlan plan = load(userId, isoWeek);
        repository.findByUserIdAndStatus(userId, WeeklyPlanStatus.ACTIVE)
                .filter(active -> !active.getId().equals(plan.getId()))
                .ifPresent(active -> {
                    throw new LifeOsException(StrategyErrorCode.STRATEGY_PLAN_ALREADY_ACTIVE,
                            "The plan for " + active.getIsoWeek() + " is still active. Close it first.",
                            Map.of("activeWeek", active.getIsoWeek()));
                });
        // Goals may have been paused or closed since the draft was written.
        List<AllocationRequest> current = plan.getAllocations().stream()
                .map(a -> new AllocationRequest(a.getGoalId(), a.getAllocatedMinutes()))
                .toList();
        toAllocations(userId, current, false);

        plan.activate(currentStrategyVersions(plan.getAllocations().stream().map(GoalAllocation::getGoalId).toList()),
                clock.instant());
        repository.saveAndFlush(plan);
        // A draft's actions are all PLANNED (statuses change only in the active week).
        List<Action> actions = actionRepository.findByWeeklyPlanIdOrderBySortOrderAscCreatedAtAsc(plan.getId());
        int plannedMinutes = actions.stream().mapToInt(Action::getEstimateMinutes).sum();
        outbox.append(new OutboxMessage("WeeklyPlan", plan.getId(), userId, WeeklyPlanActivatedV1.TYPE,
                WeeklyPlanActivatedV1.VERSION,
                weeklyPlanMapper.toActivatedEvent(plan, actions.size(), plannedMinutes)));
        return weeklyPlanMapper.toResponse(plan);
    }

    /**
     * The week's actions must still fit: their estimates (dropped ones aside) within the capacity, and their
     * goals still in the split when it is replaced.
     */
    private static void requireActionsFit(int capacityMinutes, List<GoalAllocation> allocations,
            List<Action> actions) {
        int plannedMinutes = actions.stream()
                .filter(Action::countsAgainstCapacity)
                .mapToInt(Action::getEstimateMinutes)
                .sum();
        if (plannedMinutes > capacityMinutes) {
            throw new LifeOsException(StrategyErrorCode.STRATEGY_CAPACITY_EXCEEDED,
                    "The week's actions take " + plannedMinutes + " minutes but the week would have only "
                            + capacityMinutes + ".",
                    Map.of("capacityMinutes", capacityMinutes, "plannedMinutes", plannedMinutes));
        }
        if (allocations != null) {
            Set<UUID> kept = allocations.stream().map(GoalAllocation::getGoalId).collect(Collectors.toSet());
            boolean orphaned = actions.stream().anyMatch(action -> !kept.containsAll(action.getGoalIds()));
            if (orphaned) {
                throw FieldErrors.invalid("allocations", "a removed goal still has actions in this week");
            }
        }
    }

    @Override
    @Transactional
    public WeeklyPlanResponse close(UUID userId, String isoWeek) {
        WeeklyPlan plan = load(userId, isoWeek);
        plan.close(clock.instant());
        repository.saveAndFlush(plan);
        return weeklyPlanMapper.toResponse(plan);
    }

    /**
     * Checks that every allocation names a different active goal of the user.
     *
     * @param stampStrategyVersions record each goal's current strategy version (for an active week)
     */
    private List<GoalAllocation> toAllocations(UUID userId, List<AllocationRequest> requests,
            boolean stampStrategyVersions) {
        Set<UUID> ids = requests.stream().map(AllocationRequest::goalId).collect(Collectors.toSet());
        Set<UUID> activeGoals = goalRepository.findAllById(ids).stream()
                .filter(goal -> goal.getUserId().equals(userId) && goal.getStatus() == GoalStatus.ACTIVE)
                .map(Goal::getId)
                .collect(Collectors.toSet());
        FieldErrors errors = new FieldErrors();
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < requests.size(); i++) {
            UUID goalId = requests.get(i).goalId();
            if (!activeGoals.contains(goalId)) {
                errors.add("allocations[" + i + "].goalId", "is not one of your active goals");
            } else if (!seen.add(goalId)) {
                errors.add("allocations[" + i + "].goalId", "appears more than once");
            }
        }
        errors.throwIfAny();
        Map<UUID, UUID> versions = stampStrategyVersions ? currentStrategyVersions(ids) : Map.of();
        return requests.stream()
                .map(r -> new GoalAllocation(r.goalId(), versions.get(r.goalId()), r.allocatedMinutes()))
                .toList();
    }

    private Map<UUID, UUID> currentStrategyVersions(Collection<UUID> goalIds) {
        return strategyVersionRepository.findByGoalIdInAndCurrentTrue(goalIds).stream()
                .collect(Collectors.toMap(StrategyVersion::getGoalId, StrategyVersion::getId));
    }

    private static void requireWithinCapacity(int capacityMinutes, List<GoalAllocation> allocations) {
        int allocated = allocations.stream().mapToInt(GoalAllocation::getAllocatedMinutes).sum();
        if (allocated > capacityMinutes) {
            throw new LifeOsException(StrategyErrorCode.STRATEGY_CAPACITY_EXCEEDED,
                    "The goals get " + allocated + " minutes but the week has only " + capacityMinutes + ".",
                    Map.of("capacityMinutes", capacityMinutes, "allocatedMinutes", allocated));
        }
    }

    private WeeklyPlan load(UUID userId, String isoWeek) {
        return repository.findByUserIdAndIsoWeek(userId, isoWeek)
                .orElseThrow(() -> new LifeOsException(CommonErrorCode.NOT_FOUND, "No plan for " + isoWeek + "."));
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
