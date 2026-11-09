package com.lifeos.strategy.decision.service.impl;

import com.lifeos.base.error.CommonErrorCode;
import com.lifeos.base.error.LifeOsException;
import com.lifeos.base.messaging.outbox.Outbox;
import com.lifeos.base.messaging.outbox.OutboxMessage;
import com.lifeos.strategy.common.error.FieldErrors;
import com.lifeos.strategy.common.error.StrategyErrorCode;
import com.lifeos.strategy.common.time.UserDates;
import com.lifeos.strategy.decision.dto.CloseDecisionRequest;
import com.lifeos.strategy.decision.dto.DecideRequest;
import com.lifeos.strategy.decision.dto.DecisionDraftRequest.AlternativeRequest;
import com.lifeos.strategy.decision.dto.DecisionDraftRequest.TriggerRequest;
import com.lifeos.strategy.decision.dto.DecisionDraftRequest;
import com.lifeos.strategy.decision.dto.DecisionResponse;
import com.lifeos.strategy.decision.dto.ReopenDecisionRequest;
import com.lifeos.strategy.decision.entity.Alternative;
import com.lifeos.strategy.decision.entity.Decision;
import com.lifeos.strategy.decision.entity.DecisionStatus;
import com.lifeos.strategy.decision.entity.ReopenTrigger;
import com.lifeos.strategy.decision.entity.TriggerKind;
import com.lifeos.strategy.decision.event.DecisionAssumptionFalsifiedV1;
import com.lifeos.strategy.decision.event.DecisionClosedV1;
import com.lifeos.strategy.decision.event.DecisionRecordedV1;
import com.lifeos.strategy.decision.event.DecisionReopenedV1;
import com.lifeos.strategy.decision.event.DecisionSupersededV1;
import com.lifeos.strategy.decision.mapper.DecisionMapper;
import com.lifeos.strategy.decision.repository.DecisionRepository;
import com.lifeos.strategy.decision.service.DecisionService;
import com.lifeos.strategy.goal.repository.GoalRepository;
import com.lifeos.strategy.knowledge.entity.KnowledgeItem;
import com.lifeos.strategy.knowledge.entity.KnowledgeStatus;
import com.lifeos.strategy.knowledge.repository.KnowledgeItemRepository;
import com.lifeos.strategy.profile.service.ProfileService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DecisionServiceImpl implements DecisionService {

    private final DecisionRepository repository;
    private final GoalRepository goalRepository;
    private final KnowledgeItemRepository knowledgeItemRepository;
    private final ProfileService profileService;
    private final DecisionMapper decisionMapper;
    private final Outbox outbox;
    private final Clock clock;

    public DecisionServiceImpl(DecisionRepository repository, GoalRepository goalRepository,
            KnowledgeItemRepository knowledgeItemRepository, ProfileService profileService,
            DecisionMapper decisionMapper, Outbox outbox, Clock clock) {
        this.repository = repository;
        this.goalRepository = goalRepository;
        this.knowledgeItemRepository = knowledgeItemRepository;
        this.profileService = profileService;
        this.decisionMapper = decisionMapper;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DecisionResponse create(UUID userId, DecisionDraftRequest request) {
        LocalDate today = today(userId);
        Set<UUID> assumptionIds = requireValidDraft(userId, request, today);
        Decision decision = Decision.draft(userId, request.goalId(), request.title().strip(),
                strip(request.context()), descriptions(request), rejectionReasons(request), assumptionIds,
                triggers(request), clock.instant());
        repository.saveAndFlush(decision);
        return toResponse(decision);
    }

    @Override
    @Transactional
    public DecisionResponse revise(UUID userId, UUID decisionId, DecisionDraftRequest request) {
        Decision decision = load(userId, decisionId);
        if (request.version() == null) {
            throw FieldErrors.invalid("version", "must not be null");
        }
        requireVersion(decision, request.version());
        decision.requireStatus(DecisionStatus.DRAFT);
        Set<UUID> assumptionIds = requireValidDraft(userId, request, today(userId));
        decision.revise(request.goalId(), request.title().strip(), strip(request.context()), descriptions(request),
                rejectionReasons(request), assumptionIds, triggers(request), clock.instant());
        repository.saveAndFlush(decision);
        return toResponse(decision);
    }

    @Override
    @Transactional
    public DecisionResponse decide(UUID userId, UUID decisionId, DecideRequest request) {
        Decision decision = load(userId, decisionId);
        requireVersion(decision, request.version());
        decision.requireStatus(DecisionStatus.DRAFT);

        FieldErrors errors = new FieldErrors();
        List<Alternative> alternatives = decision.getAlternatives();
        if (alternatives.size() < 2) {
            errors.add("alternatives", "a decision needs at least two alternatives");
        }
        boolean known = alternatives.stream().anyMatch(a -> a.getLabel().equals(request.chosenLabel()));
        if (!known) {
            errors.add("chosenLabel", "is not one of the alternatives");
        }
        for (int i = 0; i < alternatives.size(); i++) {
            Alternative alternative = alternatives.get(i);
            boolean rejected = !alternative.getLabel().equals(request.chosenLabel());
            if (known && rejected && isBlank(alternative.getRejectionReason())) {
                errors.add("alternatives[" + i + "].rejectionReason",
                        "say why " + alternative.getLabel() + " is not chosen");
            }
        }
        if (!request.reviewOn().isAfter(today(userId))) {
            errors.add("reviewOn", "must be in the future");
        }
        errors.throwIfAny();

        Instant now = clock.instant();
        decision.decide(request.chosenLabel(), request.expectedOutcome().strip(), request.reviewOn(), now);
        repository.saveAndFlush(decision);
        outbox.append(new OutboxMessage("Decision", decision.getId(), userId, DecisionRecordedV1.TYPE,
                DecisionRecordedV1.VERSION, decisionMapper.toRecordedEvent(decision)));
        supersedeOriginal(userId, decision, now);
        return toResponse(decision);
    }

    @Override
    @Transactional
    public DecisionResponse close(UUID userId, UUID decisionId, CloseDecisionRequest request) {
        Decision decision = load(userId, decisionId);
        requireVersion(decision, request.version());
        decision.close(request.outcomeNote().strip(), request.wouldDecideAgain(), clock.instant());
        repository.saveAndFlush(decision);
        outbox.append(new OutboxMessage("Decision", decision.getId(), userId, DecisionClosedV1.TYPE,
                DecisionClosedV1.VERSION, decisionMapper.toClosedEvent(decision)));
        return toResponse(decision);
    }

    @Override
    @Transactional
    public DecisionResponse reopen(UUID userId, UUID decisionId, ReopenDecisionRequest request) {
        Decision original = repository.findForUpdate(decisionId, userId)
                .orElseThrow(() -> new LifeOsException(CommonErrorCode.NOT_FOUND, "Decision not found."));
        repository.findByReopenedFromId(original.getId()).ifPresent(successor -> {
            throw new LifeOsException(StrategyErrorCode.STRATEGY_DECISION_ALREADY_REOPENED,
                    "This decision was already reopened; continue with that one.",
                    Map.of("reopenedAsId", successor.getId()));
        });
        Decision draft = Decision.reopen(original, request.reason().strip(), clock.instant());
        repository.saveAndFlush(draft);
        outbox.append(new OutboxMessage("Decision", draft.getId(), userId, DecisionReopenedV1.TYPE,
                DecisionReopenedV1.VERSION,
                decisionMapper.toReopenedEvent(draft, falsifiedAssumptions(List.of(original)).get(original.getId()))));
        return toResponse(draft);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DecisionResponse> list(UUID userId, DecisionStatus status, UUID goalId, boolean needsReview) {
        if (needsReview && status != null && status != DecisionStatus.OPEN) {
            return List.of();
        }
        List<Decision> decisions = repository.search(userId, needsReview ? DecisionStatus.OPEN : status, goalId);
        Map<UUID, List<UUID>> falsified = falsifiedAssumptions(decisions);
        Map<UUID, UUID> reopenedAs = repository.findByReopenedFromIdIn(
                        decisions.stream().map(Decision::getId).toList()).stream()
                .collect(Collectors.toMap(Decision::getReopenedFromId, Decision::getId));
        return decisions.stream()
                .filter(d -> !needsReview || !falsified.get(d.getId()).isEmpty())
                .map(d -> decisionMapper.toResponse(d, falsified.get(d.getId()), reopenedAs.get(d.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DecisionResponse get(UUID userId, UUID decisionId) {
        return toResponse(load(userId, decisionId));
    }

    @Override
    @Transactional
    public void assumptionFalsified(UUID userId, UUID knowledgeItemId, String statement, Instant falsifiedAt) {
        for (Decision decision : repository.findRestingOn(userId, knowledgeItemId, DecisionStatus.OPEN)) {
            outbox.append(new OutboxMessage("Decision", decision.getId(), userId,
                    DecisionAssumptionFalsifiedV1.TYPE, DecisionAssumptionFalsifiedV1.VERSION,
                    decisionMapper.toAssumptionFalsifiedEvent(decision, knowledgeItemId, statement, falsifiedAt)));
        }
    }

    /** A reopened decision was decided: an original that is still OPEN is replaced by it. */
    private void supersedeOriginal(UUID userId, Decision decided, Instant now) {
        if (decided.getReopenedFromId() == null) {
            return;
        }
        repository.findByIdAndUserId(decided.getReopenedFromId(), userId)
                .filter(original -> original.getStatus() == DecisionStatus.OPEN)
                .ifPresent(original -> {
                    original.supersede(now);
                    repository.saveAndFlush(original);
                    outbox.append(new OutboxMessage("Decision", original.getId(), userId,
                            DecisionSupersededV1.TYPE, DecisionSupersededV1.VERSION,
                            decisionMapper.toSupersededEvent(original, decided.getId())));
                });
    }

    /** For each decision, its assumptions that are FALSIFIED now (sorted; empty when none). */
    private Map<UUID, List<UUID>> falsifiedAssumptions(List<Decision> decisions) {
        Set<UUID> itemIds = decisions.stream()
                .flatMap(d -> d.getAssumptionIds().stream())
                .collect(Collectors.toSet());
        Set<UUID> falsified = knowledgeItemRepository.findAllById(itemIds).stream()
                .filter(item -> item.getStatus() == KnowledgeStatus.FALSIFIED)
                .map(KnowledgeItem::getId)
                .collect(Collectors.toSet());
        Map<UUID, List<UUID>> result = new HashMap<>();
        for (Decision decision : decisions) {
            result.put(decision.getId(), decision.getAssumptionIds().stream()
                    .filter(falsified::contains)
                    .sorted()
                    .toList());
        }
        return result;
    }

    private DecisionResponse toResponse(Decision decision) {
        UUID reopenedAs = repository.findByReopenedFromId(decision.getId()).map(Decision::getId).orElse(null);
        return decisionMapper.toResponse(decision, falsifiedAssumptions(List.of(decision)).get(decision.getId()),
                reopenedAs);
    }

    /** Checks the draft's links and triggers; returns the assumption IDs to store. */
    private Set<UUID> requireValidDraft(UUID userId, DecisionDraftRequest request, LocalDate today) {
        FieldErrors errors = new FieldErrors();
        if (request.goalId() != null && goalRepository.findByIdAndUserId(request.goalId(), userId)
                .filter(goal -> goal.getStatus().isOpen()).isEmpty()) {
            errors.add("goalId", "is not one of your open goals");
        }
        Set<UUID> assumptionIds = request.assumptionIds() != null ? request.assumptionIds() : Set.of();
        long own = knowledgeItemRepository.findAllById(assumptionIds).stream()
                .map(KnowledgeItem::getUserId)
                .filter(userId::equals)
                .count();
        if (own != assumptionIds.size()) {
            errors.add("assumptionIds", "must be items of your knowledge map");
        }
        List<TriggerRequest> triggers = request.reopenTriggers() != null ? request.reopenTriggers() : List.of();
        for (int i = 0; i < triggers.size(); i++) {
            TriggerRequest trigger = triggers.get(i);
            String field = "reopenTriggers[" + i + "].firesOn";
            if (trigger.kind() == TriggerKind.DATE && trigger.firesOn() == null) {
                errors.add(field, "is required for a DATE trigger");
            } else if (trigger.kind() == TriggerKind.DATE && !trigger.firesOn().isAfter(today)) {
                errors.add(field, "must be in the future");
            } else if (trigger.kind() == TriggerKind.EVENT && trigger.firesOn() != null) {
                errors.add(field, "only DATE triggers have a date");
            }
        }
        errors.throwIfAny();
        return assumptionIds;
    }

    private static List<String> descriptions(DecisionDraftRequest request) {
        return request.alternatives().stream().map(a -> a.description().strip()).toList();
    }

    /** Rejection reasons by label (A, B, C... in order). */
    private static Map<String, String> rejectionReasons(DecisionDraftRequest request) {
        Map<String, String> reasons = new HashMap<>();
        List<AlternativeRequest> alternatives = request.alternatives();
        for (int i = 0; i < alternatives.size(); i++) {
            String reason = strip(alternatives.get(i).rejectionReason());
            if (!isBlank(reason)) {
                reasons.put(Decision.label(i), reason);
            }
        }
        return reasons;
    }

    private static List<ReopenTrigger> triggers(DecisionDraftRequest request) {
        List<TriggerRequest> triggers = request.reopenTriggers() != null ? request.reopenTriggers() : List.of();
        return triggers.stream()
                .map(t -> new ReopenTrigger(t.kind(), t.description().strip(), t.eventType(), t.firesOn()))
                .collect(Collectors.toList());
    }

    private LocalDate today(UUID userId) {
        // Also creates the profile (foreign key) on a user's first call.
        return UserDates.today(clock, profileService.getOrCreate(userId).timezone());
    }

    private Decision load(UUID userId, UUID decisionId) {
        return repository.findByIdAndUserId(decisionId, userId)
                .orElseThrow(() -> new LifeOsException(CommonErrorCode.NOT_FOUND, "Decision not found."));
    }

    private static void requireVersion(Decision decision, int version) {
        if (decision.getVersion() != version) {
            throw new LifeOsException(CommonErrorCode.CONCURRENT_MODIFICATION,
                    "The decision was changed in the meantime. Reload it and try again.",
                    Map.of("currentVersion", decision.getVersion()));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
