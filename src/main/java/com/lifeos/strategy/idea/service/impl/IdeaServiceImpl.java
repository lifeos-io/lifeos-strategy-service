package com.lifeos.strategy.idea.service.impl;

import com.lifeos.base.error.CommonErrorCode;
import com.lifeos.base.error.LifeOsException;
import com.lifeos.base.messaging.outbox.Outbox;
import com.lifeos.base.messaging.outbox.OutboxMessage;
import com.lifeos.strategy.common.error.FieldErrors;
import com.lifeos.strategy.common.error.StrategyErrorCode;
import com.lifeos.strategy.goal.repository.GoalRepository;
import com.lifeos.strategy.idea.dto.CaptureIdeaRequest;
import com.lifeos.strategy.idea.dto.EvaluateIdeaRequest;
import com.lifeos.strategy.idea.dto.IdeaResponse;
import com.lifeos.strategy.idea.dto.PromoteIdeaRequest;
import com.lifeos.strategy.idea.dto.UpdateIdeaRequest;
import com.lifeos.strategy.idea.entity.Idea;
import com.lifeos.strategy.idea.entity.IdeaEvaluation;
import com.lifeos.strategy.idea.entity.IdeaStatus;
import com.lifeos.strategy.idea.event.IdeaChangedV1;
import com.lifeos.strategy.idea.mapper.IdeaMapper;
import com.lifeos.strategy.idea.repository.IdeaRepository;
import com.lifeos.strategy.idea.service.IdeaService;
import com.lifeos.strategy.profile.repository.UserProfileRepository;
import com.lifeos.strategy.profile.service.ProfileService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changes are flushed before the event is written, so the event carries the new version. The active limit is
 * checked under a lock on the user's profile row, so two activations of the same user run one after another and
 * the second one sees the first.
 */
@Service
public class IdeaServiceImpl implements IdeaService {

    private final IdeaRepository repository;
    private final GoalRepository goalRepository;
    private final UserProfileRepository userProfileRepository;
    private final ProfileService profileService;
    private final IdeaMapper ideaMapper;
    private final Outbox outbox;
    private final Clock clock;

    public IdeaServiceImpl(IdeaRepository repository, GoalRepository goalRepository,
            UserProfileRepository userProfileRepository, ProfileService profileService, IdeaMapper ideaMapper,
            Outbox outbox, Clock clock) {
        this.repository = repository;
        this.goalRepository = goalRepository;
        this.userProfileRepository = userProfileRepository;
        this.profileService = profileService;
        this.ideaMapper = ideaMapper;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    @Transactional
    public IdeaResponse capture(UUID userId, CaptureIdeaRequest request) {
        // Also creates the profile (foreign key) on a user's first call.
        profileService.getOrCreate(userId);
        requireOpenGoal(userId, request.goalId(), "goalId");
        Idea idea = Idea.capture(userId, request.goalId(), request.title().strip(), strip(request.note()),
                clock.instant());
        return saveAndPublish(idea);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IdeaResponse> list(UUID userId, IdeaStatus status, UUID goalId) {
        return repository.search(userId, status, goalId).stream().map(ideaMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public IdeaResponse get(UUID userId, UUID ideaId) {
        return ideaMapper.toResponse(load(userId, ideaId));
    }

    @Override
    @Transactional
    public IdeaResponse update(UUID userId, UUID ideaId, UpdateIdeaRequest request) {
        Idea idea = load(userId, ideaId);
        if (idea.getVersion() != request.version()) {
            throw new LifeOsException(CommonErrorCode.CONCURRENT_MODIFICATION,
                    "The idea was changed in the meantime. Reload it and try again.",
                    Map.of("currentVersion", idea.getVersion()));
        }
        if (request.goalId() != null && !request.goalId().equals(idea.getGoalId())) {
            requireOpenGoal(userId, request.goalId(), "goalId");
        }
        boolean changed = idea.edit(request.goalId(), strip(request.title()), strip(request.note()), clock.instant());
        return changed ? saveAndPublish(idea) : ideaMapper.toResponse(idea);
    }

    @Override
    @Transactional
    public IdeaResponse evaluate(UUID userId, UUID ideaId, EvaluateIdeaRequest request) {
        Idea idea = load(userId, ideaId);
        Instant now = clock.instant();
        idea.evaluate(new IdeaEvaluation(request.goalFit(), request.evidencePotential(), request.energy(),
                request.weeklyMinutes(), request.verdict(), strip(request.note()), now), now);
        return saveAndPublish(idea);
    }

    @Override
    @Transactional
    public IdeaResponse activate(UUID userId, UUID ideaId) {
        // Lock first, then read: a concurrent activation waits here and then counts this one.
        userProfileRepository.findForUpdate(userId);
        Idea idea = load(userId, ideaId);
        // A wrong status is reported as such, not as a full slot.
        if (idea.getStatus().canChangeTo(IdeaStatus.ACTIVE)) {
            long active = repository.countByUserIdAndStatus(userId, IdeaStatus.ACTIVE);
            if (active >= IdeaStatus.MAX_ACTIVE) {
                throw new LifeOsException(StrategyErrorCode.STRATEGY_ACTIVE_IDEA_LIMIT,
                        "You already have " + active + " active ideas. Shelve, drop or promote one first.",
                        Map.of("activeCount", active, "limit", IdeaStatus.MAX_ACTIVE));
            }
        }
        idea.activate(clock.instant());
        return saveAndPublish(idea);
    }

    @Override
    @Transactional
    public IdeaResponse shelve(UUID userId, UUID ideaId) {
        return transition(userId, ideaId, (idea, now) -> idea.shelve(now));
    }

    @Override
    @Transactional
    public IdeaResponse reopen(UUID userId, UUID ideaId) {
        return transition(userId, ideaId, (idea, now) -> idea.reopen(now));
    }

    @Override
    @Transactional
    public IdeaResponse drop(UUID userId, UUID ideaId) {
        return transition(userId, ideaId, (idea, now) -> idea.drop(now));
    }

    @Override
    @Transactional
    public IdeaResponse promote(UUID userId, UUID ideaId, PromoteIdeaRequest request) {
        Idea idea = load(userId, ideaId);
        requireOpenGoal(userId, request.goalId(), "goalId");
        idea.promote(request.goalId(), clock.instant());
        return saveAndPublish(idea);
    }

    private IdeaResponse transition(UUID userId, UUID ideaId, BiConsumer<Idea, Instant> change) {
        Idea idea = load(userId, ideaId);
        change.accept(idea, clock.instant());
        return saveAndPublish(idea);
    }

    private IdeaResponse saveAndPublish(Idea idea) {
        repository.saveAndFlush(idea);
        outbox.append(new OutboxMessage("Idea", idea.getId(), idea.getUserId(), IdeaChangedV1.TYPE,
                IdeaChangedV1.VERSION, ideaMapper.toChangedEvent(idea)));
        return ideaMapper.toResponse(idea);
    }

    /** {@code goalId} may be null (no goal); otherwise it must be one of the user's open goals. */
    private void requireOpenGoal(UUID userId, UUID goalId, String field) {
        if (goalId != null && goalRepository.findByIdAndUserId(goalId, userId)
                .filter(goal -> goal.getStatus().isOpen()).isEmpty()) {
            throw FieldErrors.invalid(field, "is not one of your open goals");
        }
    }

    private Idea load(UUID userId, UUID ideaId) {
        return repository.findByIdAndUserId(ideaId, userId)
                .orElseThrow(() -> new LifeOsException(CommonErrorCode.NOT_FOUND, "Idea not found."));
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
