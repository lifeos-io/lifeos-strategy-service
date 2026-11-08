package com.lifeos.strategy.evidence.service.impl;

import com.lifeos.base.error.CommonErrorCode;
import com.lifeos.base.error.LifeOsException;
import com.lifeos.base.messaging.outbox.Outbox;
import com.lifeos.base.messaging.outbox.OutboxMessage;
import com.lifeos.strategy.action.entity.Action;
import com.lifeos.strategy.action.repository.ActionRepository;
import com.lifeos.strategy.common.error.FieldErrors;
import com.lifeos.strategy.common.time.UserDates;
import com.lifeos.strategy.evidence.dto.EvidenceResponse;
import com.lifeos.strategy.evidence.dto.RecordEvidenceRequest;
import com.lifeos.strategy.evidence.entity.Evidence;
import com.lifeos.strategy.evidence.entity.EvidenceLevel;
import com.lifeos.strategy.evidence.event.EvidenceRecordedV1;
import com.lifeos.strategy.evidence.mapper.EvidenceMapper;
import com.lifeos.strategy.evidence.repository.EvidenceRepository;
import com.lifeos.strategy.evidence.service.EvidenceService;
import com.lifeos.strategy.feedback.repository.FeedbackRepository;
import com.lifeos.strategy.goal.entity.Goal;
import com.lifeos.strategy.goal.repository.GoalRepository;
import com.lifeos.strategy.profile.dto.ProfileResponse;
import com.lifeos.strategy.profile.service.ProfileService;
import com.lifeos.strategy.strategyversion.repository.StrategyVersionRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvidenceServiceImpl implements EvidenceService {

    private final EvidenceRepository repository;
    private final ActionRepository actionRepository;
    private final FeedbackRepository feedbackRepository;
    private final GoalRepository goalRepository;
    private final StrategyVersionRepository strategyVersionRepository;
    private final ProfileService profileService;
    private final EvidenceMapper evidenceMapper;
    private final Outbox outbox;
    private final Clock clock;

    public EvidenceServiceImpl(EvidenceRepository repository, ActionRepository actionRepository,
            FeedbackRepository feedbackRepository, GoalRepository goalRepository,
            StrategyVersionRepository strategyVersionRepository, ProfileService profileService,
            EvidenceMapper evidenceMapper, Outbox outbox, Clock clock) {
        this.repository = repository;
        this.actionRepository = actionRepository;
        this.feedbackRepository = feedbackRepository;
        this.goalRepository = goalRepository;
        this.strategyVersionRepository = strategyVersionRepository;
        this.profileService = profileService;
        this.evidenceMapper = evidenceMapper;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    @Transactional
    public EvidenceResponse record(UUID userId, RecordEvidenceRequest request) {
        // Also creates the profile (foreign key) and gives the user's time zone for "today".
        ProfileResponse profile = profileService.getOrCreate(userId);
        FieldErrors errors = new FieldErrors();

        if (request.occurredOn().isAfter(UserDates.today(clock, profile.timezone()))) {
            errors.add("occurredOn", "must not be in the future");
        }
        if (request.parentEvidenceId() != null) {
            Optional<Evidence> parent = repository.findByIdAndUserId(request.parentEvidenceId(), userId);
            if (parent.isEmpty()) {
                errors.add("parentEvidenceId", "is not one of your evidence");
            } else if (!request.level().canBuildOn(parent.get().getLevel())) {
                errors.add("parentEvidenceId", "must be a lower step than " + request.level()
                        + " (ACTIVITY → OUTPUT → OUTCOME → IMPACT), but it is " + parent.get().getLevel());
            }
        }
        Optional<Action> action = Optional.empty();
        if (request.actionId() != null) {
            action = actionRepository.findById(request.actionId()).filter(a -> a.getUserId().equals(userId));
            if (action.isEmpty()) {
                errors.add("actionId", "is not one of your actions");
            }
        }
        if (request.feedbackId() != null && feedbackRepository.findByIdAndUserId(request.feedbackId(), userId).isEmpty()) {
            errors.add("feedbackId", "is not one of your feedback");
        }

        // Evidence of an action serves the action's goals unless the user says otherwise.
        Set<UUID> goalIds = request.goalIds() != null && !request.goalIds().isEmpty()
                ? request.goalIds()
                : action.map(Action::getGoalIds).orElse(Set.of());
        Set<UUID> ownGoals = goalRepository.findAllById(goalIds).stream()
                .filter(goal -> goal.getUserId().equals(userId))
                .map(Goal::getId)
                .collect(Collectors.toSet());
        if (!ownGoals.containsAll(goalIds)) {
            errors.add("goalIds", "must be your goals");
        }
        Set<UUID> phaseKeys = request.phaseKeys() != null ? request.phaseKeys() : Set.of();
        if (!phaseKeys.isEmpty()) {
            if (goalIds.isEmpty()) {
                errors.add("phaseKeys", "need the goals the evidence serves (goalIds or an action)");
            } else if (!phaseKeys.stream().allMatch(key -> strategyVersionRepository.existsCurrentPhase(key, goalIds))) {
                errors.add("phaseKeys", "must be phases of these goals' current strategy");
            }
        }
        errors.throwIfAny();

        Evidence evidence = Evidence.record(userId, request.title().strip(), request.level(), request.strength(),
                request.occurredOn(), request.parentEvidenceId(), request.actionId(), request.feedbackId(),
                strip(request.note()), goalIds, phaseKeys, clock.instant());
        repository.saveAndFlush(evidence);
        outbox.append(new OutboxMessage("Evidence", evidence.getId(), userId, EvidenceRecordedV1.TYPE,
                EvidenceRecordedV1.VERSION, evidenceMapper.toRecordedEvent(evidence)));
        return evidenceMapper.toResponse(evidence);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceResponse> list(UUID userId, UUID goalId, EvidenceLevel level) {
        return repository.search(userId, goalId, level).stream().map(evidenceMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EvidenceResponse get(UUID userId, UUID evidenceId) {
        return repository.findByIdAndUserId(evidenceId, userId)
                .map(evidenceMapper::toResponse)
                .orElseThrow(() -> new LifeOsException(CommonErrorCode.NOT_FOUND, "Evidence not found."));
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
