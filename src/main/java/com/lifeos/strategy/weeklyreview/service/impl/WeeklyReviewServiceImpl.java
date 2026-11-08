package com.lifeos.strategy.weeklyreview.service.impl;

import com.lifeos.base.error.CommonErrorCode;
import com.lifeos.base.error.LifeOsException;
import com.lifeos.base.messaging.outbox.Outbox;
import com.lifeos.base.messaging.outbox.OutboxMessage;
import com.lifeos.strategy.action.entity.Action;
import com.lifeos.strategy.action.entity.ActionStatus;
import com.lifeos.strategy.action.repository.ActionRepository;
import com.lifeos.strategy.common.error.StrategyErrorCode;
import com.lifeos.strategy.evidence.repository.EvidenceRepository;
import com.lifeos.strategy.weeklyplan.entity.WeeklyPlan;
import com.lifeos.strategy.weeklyplan.entity.WeeklyPlanStatus;
import com.lifeos.strategy.weeklyplan.repository.WeeklyPlanRepository;
import com.lifeos.strategy.weeklyplan.service.WeeklyPlanService;
import com.lifeos.strategy.weeklyreview.dto.CompleteWeeklyReviewRequest;
import com.lifeos.strategy.weeklyreview.dto.WeeklyReviewPreviewResponse;
import com.lifeos.strategy.weeklyreview.dto.WeeklyReviewResponse;
import com.lifeos.strategy.weeklyreview.entity.ActionTally;
import com.lifeos.strategy.weeklyreview.entity.GoalOutcome;
import com.lifeos.strategy.weeklyreview.entity.WeeklyReview;
import com.lifeos.strategy.weeklyreview.event.WeeklyReviewCompletedV1;
import com.lifeos.strategy.weeklyreview.mapper.WeeklyReviewMapper;
import com.lifeos.strategy.weeklyreview.repository.WeeklyReviewRepository;
import com.lifeos.strategy.weeklyreview.service.WeeklyReviewService;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the week (plan, actions, evidence) from their own modules and closes it through
 * {@link WeeklyPlanService}, so the week's rules stay in one place. A second review of the same week finds it
 * closed; two racing ones collide on the plan's version.
 */
@Service
public class WeeklyReviewServiceImpl implements WeeklyReviewService {

    private final WeeklyReviewRepository repository;
    private final WeeklyPlanRepository weeklyPlanRepository;
    private final ActionRepository actionRepository;
    private final EvidenceRepository evidenceRepository;
    private final WeeklyPlanService weeklyPlanService;
    private final WeeklyReviewMapper weeklyReviewMapper;
    private final Outbox outbox;
    private final Clock clock;

    public WeeklyReviewServiceImpl(WeeklyReviewRepository repository, WeeklyPlanRepository weeklyPlanRepository,
            ActionRepository actionRepository, EvidenceRepository evidenceRepository,
            WeeklyPlanService weeklyPlanService, WeeklyReviewMapper weeklyReviewMapper, Outbox outbox, Clock clock) {
        this.repository = repository;
        this.weeklyPlanRepository = weeklyPlanRepository;
        this.actionRepository = actionRepository;
        this.evidenceRepository = evidenceRepository;
        this.weeklyPlanService = weeklyPlanService;
        this.weeklyReviewMapper = weeklyReviewMapper;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public WeeklyReviewPreviewResponse preview(UUID userId, String isoWeek) {
        WeeklyPlan plan = loadActivePlan(userId, isoWeek);
        List<Action> actions = actionsOf(plan);
        return weeklyReviewMapper.toPreviewResponse(plan.getIsoWeek(), plan.getWeekStart(),
                plan.getCapacityMinutes(), tally(actions), goalOutcomes(plan, actions), evidenceCount(plan));
    }

    @Override
    @Transactional
    public WeeklyReviewResponse complete(UUID userId, String isoWeek, CompleteWeeklyReviewRequest request) {
        WeeklyPlan plan = loadActivePlan(userId, isoWeek);
        List<Action> actions = actionsOf(plan);
        WeeklyReview review = WeeklyReview.complete(userId, plan.getId(), plan.getIsoWeek(),
                plan.getCapacityMinutes(), tally(actions), goalOutcomes(plan, actions), evidenceCount(plan),
                request.energy(), strip(request.wentWell()), strip(request.gotStuck()), strip(request.lesson()),
                strip(request.nextWeekFocus()), clock.instant());
        weeklyPlanService.close(userId, isoWeek);
        repository.saveAndFlush(review);
        outbox.append(new OutboxMessage("WeeklyReview", review.getId(), userId, WeeklyReviewCompletedV1.TYPE,
                WeeklyReviewCompletedV1.VERSION, weeklyReviewMapper.toCompletedEvent(review, plan.getWeekStart())));
        return weeklyReviewMapper.toResponse(review);
    }

    @Override
    @Transactional(readOnly = true)
    public WeeklyReviewResponse get(UUID userId, String isoWeek) {
        return repository.findByUserIdAndIsoWeek(userId, isoWeek)
                .map(weeklyReviewMapper::toResponse)
                .orElseThrow(() -> new LifeOsException(CommonErrorCode.NOT_FOUND, "No review for " + isoWeek + "."));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WeeklyReviewResponse> list(UUID userId) {
        return repository.findByUserIdOrderByIsoWeekDesc(userId).stream()
                .map(weeklyReviewMapper::toResponse)
                .toList();
    }

    /** Only the active week can be reviewed: a draft has not happened yet, a closed week is history. */
    private WeeklyPlan loadActivePlan(UUID userId, String isoWeek) {
        WeeklyPlan plan = weeklyPlanRepository.findByUserIdAndIsoWeek(userId, isoWeek)
                .orElseThrow(() -> new LifeOsException(CommonErrorCode.NOT_FOUND, "No plan for " + isoWeek + "."));
        if (plan.getStatus() != WeeklyPlanStatus.ACTIVE) {
            throw new LifeOsException(StrategyErrorCode.STRATEGY_PLAN_STATUS,
                    "The plan for " + isoWeek + " is " + plan.getStatus().name().toLowerCase()
                            + "; only the active week can be reviewed.",
                    Map.of("currentStatus", plan.getStatus().name()));
        }
        return plan;
    }

    private List<Action> actionsOf(WeeklyPlan plan) {
        return actionRepository.findByWeeklyPlanIdOrderBySortOrderAscCreatedAtAsc(plan.getId());
    }

    private static ActionTally tally(List<Action> actions) {
        return new ActionTally(
                count(actions, ActionStatus.PLANNED), count(actions, ActionStatus.DONE),
                count(actions, ActionStatus.DEFERRED), count(actions, ActionStatus.DROPPED),
                actions.stream().filter(Action::countsAgainstCapacity).mapToInt(Action::getEstimateMinutes).sum(),
                doneMinutes(actions));
    }

    /** Each goal of the split with the estimates of its done actions (an action of two goals counts for both). */
    private static List<GoalOutcome> goalOutcomes(WeeklyPlan plan, List<Action> actions) {
        return plan.getAllocations().stream()
                .map(allocation -> new GoalOutcome(allocation.getGoalId(), allocation.getAllocatedMinutes(),
                        doneMinutes(actions.stream()
                                .filter(a -> a.getGoalIds().contains(allocation.getGoalId()))
                                .toList())))
                .toList();
    }

    /** Evidence that occurred from Monday to Sunday of the week. */
    private int evidenceCount(WeeklyPlan plan) {
        return Math.toIntExact(evidenceRepository.countByUserIdAndOccurredOnBetween(plan.getUserId(),
                plan.getWeekStart(), plan.getWeekStart().plusDays(6)));
    }

    private static int count(List<Action> actions, ActionStatus status) {
        return (int) actions.stream().filter(a -> a.getStatus() == status).count();
    }

    private static int doneMinutes(List<Action> actions) {
        return actions.stream()
                .filter(a -> a.getStatus() == ActionStatus.DONE)
                .mapToInt(Action::getEstimateMinutes)
                .sum();
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
