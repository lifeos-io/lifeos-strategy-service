package com.lifeos.strategy.strategyversion.service.impl;

import com.lifeos.base.error.CommonErrorCode;
import com.lifeos.base.error.LifeOsException;
import com.lifeos.base.id.UuidV7;
import com.lifeos.base.messaging.outbox.Outbox;
import com.lifeos.base.messaging.outbox.OutboxMessage;
import com.lifeos.strategy.common.error.FieldErrors;
import com.lifeos.strategy.common.error.StrategyErrorCode;
import com.lifeos.strategy.goal.entity.Goal;
import com.lifeos.strategy.goal.repository.GoalRepository;
import com.lifeos.strategy.strategyversion.dto.CreateStrategyVersionRequest;
import com.lifeos.strategy.strategyversion.dto.CreateStrategyVersionRequest.PhaseRequest;
import com.lifeos.strategy.strategyversion.dto.StrategyVersionResponse;
import com.lifeos.strategy.strategyversion.entity.Phase;
import com.lifeos.strategy.strategyversion.entity.PhaseStatus;
import com.lifeos.strategy.strategyversion.entity.StrategyVersion;
import com.lifeos.strategy.strategyversion.event.StrategyRevisedV1;
import com.lifeos.strategy.strategyversion.mapper.StrategyVersionMapper;
import com.lifeos.strategy.strategyversion.repository.StrategyVersionRepository;
import com.lifeos.strategy.strategyversion.service.StrategyVersionService;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A new version locks the goal row first, so two revisions of the same goal run one after another; together
 * with {@code expectedCurrentVersionNo} this keeps a revision from silently replacing one the client never saw.
 */
@Service
public class StrategyVersionServiceImpl implements StrategyVersionService {

    static final String INITIAL_CHANGE_REASON = "Initial strategy";

    private final StrategyVersionRepository repository;
    private final GoalRepository goalRepository;
    private final StrategyVersionMapper strategyVersionMapper;
    private final Outbox outbox;
    private final Clock clock;

    public StrategyVersionServiceImpl(StrategyVersionRepository repository, GoalRepository goalRepository,
            StrategyVersionMapper strategyVersionMapper, Outbox outbox, Clock clock) {
        this.repository = repository;
        this.goalRepository = goalRepository;
        this.strategyVersionMapper = strategyVersionMapper;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    @Transactional
    public StrategyVersionResponse create(UUID userId, UUID goalId, CreateStrategyVersionRequest request) {
        Goal goal = goalRepository.findForUpdate(goalId, userId).orElseThrow(StrategyVersionServiceImpl::goalNotFound);
        if (!goal.getStatus().isOpen()) {
            throw new LifeOsException(StrategyErrorCode.STRATEGY_GOAL_CLOSED,
                    "This goal is " + goal.getStatus().name().toLowerCase() + "; its strategy can no longer change.");
        }
        Optional<StrategyVersion> current = repository.findByGoalIdAndCurrentTrue(goalId);
        Integer currentNo = current.map(StrategyVersion::getVersionNo).orElse(null);
        if (!Objects.equals(currentNo, request.expectedCurrentVersionNo())) {
            // No currentVersionNo member means the goal has no strategy yet (problem members cannot be null).
            Map<String, Object> details = currentNo == null ? Map.of() : Map.of("currentVersionNo", currentNo);
            throw new LifeOsException(CommonErrorCode.CONCURRENT_MODIFICATION,
                    "The strategy was revised in the meantime. Reload it and try again.", details);
        }
        String changeReason = validate(request, goal, current.orElse(null));

        current.ifPresent(previous -> {
            previous.retire();
            // Before the new row: only one version per goal may be current (unique index).
            repository.saveAndFlush(previous);
        });
        StrategyVersion version = StrategyVersion.create(goalId, userId, currentNo == null ? 1 : currentNo + 1,
                request.summary().strip(), changeReason, null, request.effortAllocation(), clock.instant());
        for (PhaseRequest phase : request.phases()) {
            version.addPhase(phase.phaseKey() != null ? phase.phaseKey() : UuidV7.next(), phase.title().strip(),
                    phase.startsOn(), phase.endsOn(), strip(phase.exitCriteria()), phase.status());
        }
        repository.saveAndFlush(version);
        outbox.append(new OutboxMessage("StrategyVersion", version.getId(), userId, StrategyRevisedV1.TYPE,
                StrategyRevisedV1.VERSION, strategyVersionMapper.toRevisedEvent(version, currentNo)));
        return strategyVersionMapper.toResponse(version);
    }

    @Override
    @Transactional(readOnly = true)
    public StrategyVersionResponse getCurrent(UUID userId, UUID goalId) {
        requireGoal(userId, goalId);
        return repository.findByGoalIdAndCurrentTrue(goalId)
                .map(strategyVersionMapper::toResponse)
                .orElseThrow(() -> new LifeOsException(CommonErrorCode.NOT_FOUND, "This goal has no strategy yet."));
    }

    @Override
    @Transactional(readOnly = true)
    public StrategyVersionResponse get(UUID userId, UUID goalId, int versionNo) {
        requireGoal(userId, goalId);
        return repository.findByGoalIdAndVersionNo(goalId, versionNo)
                .map(strategyVersionMapper::toResponse)
                .orElseThrow(() -> new LifeOsException(CommonErrorCode.NOT_FOUND, "Strategy version not found."));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StrategyVersionResponse> list(UUID userId, UUID goalId) {
        requireGoal(userId, goalId);
        return repository.findByGoalIdOrderByVersionNoDesc(goalId).stream()
                .map(strategyVersionMapper::toResponse)
                .toList();
    }

    /** Checks the rules that need the goal or the current version; returns the change reason to store. */
    private static String validate(CreateStrategyVersionRequest request, Goal goal, StrategyVersion current) {
        FieldErrors errors = new FieldErrors();

        String changeReason = strip(request.changeReason());
        if (changeReason == null || changeReason.isEmpty()) {
            if (current != null) {
                errors.add("changeReason", "is required when revising a strategy");
            }
            changeReason = INITIAL_CHANGE_REASON;
        }

        int total = request.effortAllocation().values().stream().mapToInt(Integer::intValue).sum();
        if (total != 100) {
            errors.add("effortAllocation", "must add up to 100, got " + total);
        }

        Set<UUID> carriedOver = current == null ? Set.of()
                : current.getPhases().stream().map(Phase::getPhaseKey).collect(Collectors.toSet());
        Set<UUID> seenKeys = new HashSet<>();
        long active = 0;
        for (int i = 0; i < request.phases().size(); i++) {
            PhaseRequest phase = request.phases().get(i);
            String field = "phases[" + i + "].";
            if (phase.phaseKey() != null) {
                if (!carriedOver.contains(phase.phaseKey())) {
                    errors.add(field + "phaseKey", "is not a phase of the current strategy version");
                } else if (!seenKeys.add(phase.phaseKey())) {
                    errors.add(field + "phaseKey", "appears more than once");
                }
            }
            if (phase.endsOn().isBefore(phase.startsOn())) {
                errors.add(field + "endsOn", "must not be before startsOn");
            }
            if (phase.startsOn().isBefore(goal.getWindowStart())) {
                errors.add(field + "startsOn", "must not be before the goal starts (" + goal.getWindowStart() + ")");
            }
            if (phase.endsOn().isAfter(goal.getWindowEnd())) {
                errors.add(field + "endsOn", "must not be after the goal ends (" + goal.getWindowEnd() + ")");
            }
            if (phase.status() == PhaseStatus.ACTIVE) {
                active++;
            }
        }
        if (active > 1) {
            errors.add("phases", "at most one phase can be ACTIVE");
        }

        errors.throwIfAny();
        return changeReason;
    }

    private void requireGoal(UUID userId, UUID goalId) {
        goalRepository.findByIdAndUserId(goalId, userId).orElseThrow(StrategyVersionServiceImpl::goalNotFound);
    }

    private static LifeOsException goalNotFound() {
        return new LifeOsException(CommonErrorCode.NOT_FOUND, "Goal not found.");
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
