package com.lifeos.strategy.action.service.impl;

import com.lifeos.base.error.CommonErrorCode;
import com.lifeos.base.error.LifeOsException;
import com.lifeos.base.messaging.outbox.Outbox;
import com.lifeos.base.messaging.outbox.OutboxMessage;
import com.lifeos.strategy.action.dto.ActionResponse;
import com.lifeos.strategy.action.dto.ChangeActionStatusRequest;
import com.lifeos.strategy.action.dto.CreateActionRequest;
import com.lifeos.strategy.action.dto.UpdateActionRequest;
import com.lifeos.strategy.action.entity.Action;
import com.lifeos.strategy.action.entity.ActionStatus;
import com.lifeos.strategy.action.event.ActionStatusChangedV1;
import com.lifeos.strategy.action.mapper.ActionMapper;
import com.lifeos.strategy.action.repository.ActionRepository;
import com.lifeos.strategy.action.service.ActionService;
import com.lifeos.strategy.common.error.FieldErrors;
import com.lifeos.strategy.common.error.StrategyErrorCode;
import com.lifeos.strategy.strategyversion.repository.StrategyVersionRepository;
import com.lifeos.strategy.weeklyplan.entity.GoalAllocation;
import com.lifeos.strategy.weeklyplan.entity.WeeklyPlan;
import com.lifeos.strategy.weeklyplan.entity.WeeklyPlanStatus;
import com.lifeos.strategy.weeklyplan.repository.WeeklyPlanRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActionServiceImpl implements ActionService {

    private final ActionRepository repository;
    private final WeeklyPlanRepository weeklyPlanRepository;
    private final StrategyVersionRepository strategyVersionRepository;
    private final ActionMapper actionMapper;
    private final Outbox outbox;
    private final Clock clock;

    public ActionServiceImpl(ActionRepository repository, WeeklyPlanRepository weeklyPlanRepository,
            StrategyVersionRepository strategyVersionRepository, ActionMapper actionMapper, Outbox outbox,
            Clock clock) {
        this.repository = repository;
        this.weeklyPlanRepository = weeklyPlanRepository;
        this.strategyVersionRepository = strategyVersionRepository;
        this.actionMapper = actionMapper;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ActionResponse create(UUID userId, String isoWeek, CreateActionRequest request) {
        WeeklyPlan plan = loadPlan(userId, isoWeek);
        plan.requireEditable();
        requireValidLinks(plan, request.goalIds(), request.phaseKey());
        List<Action> weekActions = repository.findByWeeklyPlanIdOrderBySortOrderAscCreatedAtAsc(plan.getId());
        requireWithinCapacity(plan, plannedMinutes(weekActions) + request.estimateMinutes());

        int sortOrder = weekActions.stream().mapToInt(Action::getSortOrder).max().orElse(0) + 1;
        Action action = Action.create(plan.getId(), userId, request.goalIds(), request.phaseKey(),
                request.title().strip(), strip(request.why()), strip(request.expectedEvidence()), request.kind(),
                request.estimateMinutes(), sortOrder, clock.instant());
        repository.saveAndFlush(action);
        if (plan.getStatus() == WeeklyPlanStatus.ACTIVE) {
            publishStatusChanged(action, plan, null);
        }
        return actionMapper.toResponse(action);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActionResponse> list(UUID userId, String isoWeek) {
        WeeklyPlan plan = loadPlan(userId, isoWeek);
        return repository.findByWeeklyPlanIdOrderBySortOrderAscCreatedAtAsc(plan.getId()).stream()
                .map(actionMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ActionResponse get(UUID userId, String isoWeek, UUID actionId) {
        return actionMapper.toResponse(loadAction(loadPlan(userId, isoWeek), actionId));
    }

    @Override
    @Transactional
    public ActionResponse update(UUID userId, String isoWeek, UUID actionId, UpdateActionRequest request) {
        WeeklyPlan plan = loadPlan(userId, isoWeek);
        Action action = loadAction(plan, actionId);
        requireVersion(action, request.version());
        plan.requireEditable();
        if (request.goalIds() != null || request.phaseKey() != null) {
            requireValidLinks(plan,
                    request.goalIds() != null ? request.goalIds() : action.getGoalIds(),
                    request.phaseKey() != null ? request.phaseKey() : action.getPhaseKey());
        }
        if (request.estimateMinutes() != null && action.countsAgainstCapacity()) {
            List<Action> weekActions = repository.findByWeeklyPlanIdOrderBySortOrderAscCreatedAtAsc(plan.getId());
            requireWithinCapacity(plan,
                    plannedMinutes(weekActions) - action.getEstimateMinutes() + request.estimateMinutes());
        }
        if (action.update(request.goalIds(), request.phaseKey(), strip(request.title()), strip(request.why()),
                strip(request.expectedEvidence()), request.kind(), request.estimateMinutes(), clock.instant())) {
            repository.saveAndFlush(action);
        }
        return actionMapper.toResponse(action);
    }

    @Override
    @Transactional
    public ActionResponse changeStatus(UUID userId, String isoWeek, UUID actionId,
            ChangeActionStatusRequest request) {
        WeeklyPlan plan = loadPlan(userId, isoWeek);
        Action action = loadAction(plan, actionId);
        requireVersion(action, request.version());
        if (plan.getStatus() != WeeklyPlanStatus.ACTIVE) {
            throw new LifeOsException(StrategyErrorCode.STRATEGY_PLAN_STATUS,
                    "Actions change status only in the active week; " + isoWeek + " is "
                            + plan.getStatus().name().toLowerCase() + ".",
                    Map.of("status", plan.getStatus().name()));
        }
        if (!action.countsAgainstCapacity() && request.status() != ActionStatus.DROPPED) {
            // Picking a dropped action up again puts its estimate back on the week.
            List<Action> weekActions = repository.findByWeeklyPlanIdOrderBySortOrderAscCreatedAtAsc(plan.getId());
            requireWithinCapacity(plan, plannedMinutes(weekActions) + action.getEstimateMinutes());
        }
        ActionStatus previous = action.getStatus();
        if (action.changeStatus(request.status(), clock.instant())) {
            repository.saveAndFlush(action);
            publishStatusChanged(action, plan, previous);
        }
        return actionMapper.toResponse(action);
    }

    @Override
    @Transactional
    public void delete(UUID userId, String isoWeek, UUID actionId) {
        WeeklyPlan plan = loadPlan(userId, isoWeek);
        Action action = loadAction(plan, actionId);
        if (plan.getStatus() != WeeklyPlanStatus.DRAFT) {
            throw new LifeOsException(StrategyErrorCode.STRATEGY_PLAN_STATUS,
                    "Only a draft week's actions can be deleted; drop the action instead.",
                    Map.of("status", plan.getStatus().name()));
        }
        repository.delete(action);
    }

    /** Goals must have minutes in this week; the phase must belong to one of their current strategies. */
    private void requireValidLinks(WeeklyPlan plan, Set<UUID> goalIds, UUID phaseKey) {
        Set<UUID> planned = plan.getAllocations().stream()
                .map(GoalAllocation::getGoalId)
                .collect(Collectors.toSet());
        FieldErrors errors = new FieldErrors();
        if (!planned.containsAll(goalIds)) {
            errors.add("goalIds", "every goal must have minutes in this week's plan");
        }
        if (phaseKey != null && !strategyVersionRepository.existsCurrentPhase(phaseKey, goalIds)) {
            errors.add("phaseKey", "is not a phase of these goals' current strategy");
        }
        errors.throwIfAny();
    }

    private static void requireWithinCapacity(WeeklyPlan plan, int plannedMinutes) {
        if (plannedMinutes > plan.getCapacityMinutes()) {
            throw new LifeOsException(StrategyErrorCode.STRATEGY_CAPACITY_EXCEEDED,
                    "The week's actions would take " + plannedMinutes + " minutes but the week has only "
                            + plan.getCapacityMinutes() + ".",
                    Map.of("capacityMinutes", plan.getCapacityMinutes(), "plannedMinutes", plannedMinutes));
        }
    }

    private static int plannedMinutes(List<Action> weekActions) {
        return weekActions.stream()
                .filter(Action::countsAgainstCapacity)
                .mapToInt(Action::getEstimateMinutes)
                .sum();
    }

    private static void requireVersion(Action action, int version) {
        if (action.getVersion() != version) {
            throw new LifeOsException(CommonErrorCode.CONCURRENT_MODIFICATION,
                    "The action was changed in the meantime. Reload it and try again.",
                    Map.of("currentVersion", action.getVersion()));
        }
    }

    private void publishStatusChanged(Action action, WeeklyPlan plan, ActionStatus previousStatus) {
        List<Action> weekActions = repository.findByWeeklyPlanIdOrderBySortOrderAscCreatedAtAsc(plan.getId());
        outbox.append(new OutboxMessage("Action", action.getId(), action.getUserId(), ActionStatusChangedV1.TYPE,
                ActionStatusChangedV1.VERSION,
                actionMapper.toStatusChangedEvent(action, plan.getIsoWeek(), previousStatus, weekActions)));
    }

    private WeeklyPlan loadPlan(UUID userId, String isoWeek) {
        return weeklyPlanRepository.findByUserIdAndIsoWeek(userId, isoWeek)
                .orElseThrow(() -> new LifeOsException(CommonErrorCode.NOT_FOUND, "No plan for " + isoWeek + "."));
    }

    private Action loadAction(WeeklyPlan plan, UUID actionId) {
        return repository.findByIdAndWeeklyPlanId(actionId, plan.getId())
                .orElseThrow(() -> new LifeOsException(CommonErrorCode.NOT_FOUND, "Action not found."));
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
