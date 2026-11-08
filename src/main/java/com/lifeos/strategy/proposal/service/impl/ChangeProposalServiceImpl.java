package com.lifeos.strategy.proposal.service.impl;

import com.lifeos.base.error.CommonErrorCode;
import com.lifeos.base.error.LifeOsException;
import com.lifeos.base.messaging.MalformedEventException;
import com.lifeos.base.messaging.outbox.Outbox;
import com.lifeos.base.messaging.outbox.OutboxMessage;
import com.lifeos.strategy.common.error.StrategyErrorCode;
import com.lifeos.strategy.goal.entity.Goal;
import com.lifeos.strategy.goal.repository.GoalRepository;
import com.lifeos.strategy.proposal.dto.ChangeProposalResponse;
import com.lifeos.strategy.proposal.dto.RejectProposalRequest;
import com.lifeos.strategy.proposal.entity.ChangeProposal;
import com.lifeos.strategy.proposal.entity.ProposalSource;
import com.lifeos.strategy.proposal.entity.ProposalStatus;
import com.lifeos.strategy.proposal.event.ProposalDecidedV1;
import com.lifeos.strategy.proposal.event.RecommendationProposedV1;
import com.lifeos.strategy.proposal.event.RecommendationProposedV1.RevisionV1;
import com.lifeos.strategy.proposal.mapper.ChangeProposalMapper;
import com.lifeos.strategy.proposal.repository.ChangeProposalRepository;
import com.lifeos.strategy.proposal.service.ChangeProposalService;
import com.lifeos.strategy.strategyversion.dto.CreateStrategyVersionRequest;
import com.lifeos.strategy.strategyversion.dto.StrategyVersionResponse;
import com.lifeos.strategy.strategyversion.entity.StrategyVersion;
import com.lifeos.strategy.strategyversion.repository.StrategyVersionRepository;
import com.lifeos.strategy.strategyversion.service.StrategyVersionService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receiving locks the goal row, so two recommendations for the same goal are stored one after another and the
 * second supersedes the first. Accepting goes through {@link StrategyVersionService}, so a proposed version
 * passes the same rules as one the user writes.
 */
@Service
public class ChangeProposalServiceImpl implements ChangeProposalService {

    private static final Logger log = LoggerFactory.getLogger(ChangeProposalServiceImpl.class);

    private final ChangeProposalRepository repository;
    private final GoalRepository goalRepository;
    private final StrategyVersionRepository strategyVersionRepository;
    private final StrategyVersionService strategyVersionService;
    private final ChangeProposalMapper changeProposalMapper;
    private final Validator validator;
    private final Outbox outbox;
    private final Clock clock;

    public ChangeProposalServiceImpl(ChangeProposalRepository repository, GoalRepository goalRepository,
            StrategyVersionRepository strategyVersionRepository, StrategyVersionService strategyVersionService,
            ChangeProposalMapper changeProposalMapper, Validator validator, Outbox outbox, Clock clock) {
        this.repository = repository;
        this.goalRepository = goalRepository;
        this.strategyVersionRepository = strategyVersionRepository;
        this.strategyVersionService = strategyVersionService;
        this.changeProposalMapper = changeProposalMapper;
        this.validator = validator;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void receive(UUID userId, RecommendationProposedV1 recommendation) {
        requireWellFormed(recommendation);
        if (repository.existsByRecommendationId(recommendation.recommendationId())) {
            log.info("Skipping recommendation {}: already stored", recommendation.recommendationId());
            return;
        }
        Goal goal = goalRepository.findForUpdate(recommendation.goalId(), userId).orElse(null);
        if (goal == null || !goal.getStatus().isOpen()) {
            log.info("Skipping recommendation {}: goal {} is not an open goal of the user",
                    recommendation.recommendationId(), recommendation.goalId());
            return;
        }
        Instant now = clock.instant();
        repository.findByGoalIdAndStatus(goal.getId(), ProposalStatus.PENDING).ifPresent(pending -> {
            pending.supersede(now);
            // Before the new row: only one proposal per goal may be pending (unique index).
            repository.saveAndFlush(pending);
            publishDecided(pending);
        });
        Set<ProposalSource> sources = recommendation.sources() == null ? Set.of()
                : recommendation.sources().stream()
                        .map(s -> new ProposalSource(s.type(), s.id()))
                        .collect(Collectors.toSet());
        ChangeProposal proposal = ChangeProposal.receive(userId, recommendation.recommendationId(),
                recommendation.kind(), goal.getId(), recommendation.basedOnVersionNo(),
                recommendation.rationale().strip(), changeProposalMapper.writeRevision(recommendation.revision()),
                sources, now);
        repository.saveAndFlush(proposal);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChangeProposalResponse> list(UUID userId, ProposalStatus status, UUID goalId) {
        List<ChangeProposal> proposals = repository.search(userId, status, goalId);
        Set<UUID> applicable = applicable(proposals);
        return proposals.stream()
                .map(p -> changeProposalMapper.toResponse(p, applicable.contains(p.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ChangeProposalResponse get(UUID userId, UUID proposalId) {
        return toResponse(load(userId, proposalId));
    }

    @Override
    @Transactional
    public ChangeProposalResponse accept(UUID userId, UUID proposalId) {
        ChangeProposal proposal = load(userId, proposalId);
        proposal.requirePending();
        Integer currentNo = strategyVersionRepository.findByGoalIdAndCurrentTrue(proposal.getGoalId())
                .map(StrategyVersion::getVersionNo)
                .orElse(null);
        if (!Objects.equals(currentNo, proposal.getBasedOnVersionNo())) {
            Map<String, Object> details = new HashMap<>();
            if (proposal.getBasedOnVersionNo() != null) {
                details.put("basedOnVersionNo", proposal.getBasedOnVersionNo());
            }
            if (currentNo != null) {
                details.put("currentVersionNo", currentNo);
            }
            throw new LifeOsException(StrategyErrorCode.STRATEGY_PROPOSAL_OUTDATED,
                    "The strategy changed after Coach made this proposal. Reject it, or revise the strategy "
                            + "yourself.",
                    details);
        }
        CreateStrategyVersionRequest request = changeProposalMapper.toStrategyVersionRequest(
                changeProposalMapper.readRevision(proposal), proposal.getBasedOnVersionNo());
        StrategyVersionResponse version = strategyVersionService.create(userId, proposal.getGoalId(), request,
                proposal.getId());
        proposal.accept(version.id(), clock.instant());
        repository.saveAndFlush(proposal);
        publishDecided(proposal);
        return changeProposalMapper.toResponse(proposal, false);
    }

    @Override
    @Transactional
    public ChangeProposalResponse reject(UUID userId, UUID proposalId, RejectProposalRequest request) {
        ChangeProposal proposal = load(userId, proposalId);
        String note = request == null || request.note() == null || request.note().isBlank()
                ? null : request.note().strip();
        proposal.reject(note, clock.instant());
        repository.saveAndFlush(proposal);
        publishDecided(proposal);
        return changeProposalMapper.toResponse(proposal, false);
    }

    /**
     * A recommendation that can never become a strategy version (missing fields, a revision that breaks the
     * request's constraints) is malformed: retrying does not help, so it goes to the dead-letter topic. Rules
     * that depend on the current state (dates within the goal, carried-over phase keys) are checked on accept.
     */
    private void requireWellFormed(RecommendationProposedV1 recommendation) {
        if (recommendation.recommendationId() == null || recommendation.kind() == null
                || recommendation.goalId() == null || recommendation.rationale() == null
                || recommendation.rationale().isBlank() || recommendation.rationale().length() > 2000
                || recommendation.revision() == null) {
            throw new MalformedEventException("RecommendationProposed is missing required fields", null);
        }
        RevisionV1 revision = recommendation.revision();
        Set<ConstraintViolation<CreateStrategyVersionRequest>> violations = validator.validate(
                changeProposalMapper.toStrategyVersionRequest(revision, recommendation.basedOnVersionNo()));
        if (!violations.isEmpty() || revision.changeReason() == null || revision.changeReason().isBlank()) {
            String problems = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new MalformedEventException("RecommendationProposed " + recommendation.recommendationId()
                    + " has an invalid revision: " + (problems.isEmpty() ? "changeReason is blank" : problems),
                    null);
        }
    }

    /** IDs of the proposals that could be accepted now. */
    private Set<UUID> applicable(List<ChangeProposal> proposals) {
        List<ChangeProposal> pending = proposals.stream()
                .filter(p -> p.getStatus() == ProposalStatus.PENDING)
                .toList();
        if (pending.isEmpty()) {
            return Set.of();
        }
        Set<UUID> goalIds = pending.stream().map(ChangeProposal::getGoalId).collect(Collectors.toSet());
        Set<UUID> openGoals = goalRepository.findAllById(goalIds).stream()
                .filter(goal -> goal.getStatus().isOpen())
                .map(Goal::getId)
                .collect(Collectors.toSet());
        Map<UUID, Integer> currentNos = strategyVersionRepository.findByGoalIdInAndCurrentTrue(goalIds).stream()
                .collect(Collectors.toMap(StrategyVersion::getGoalId, StrategyVersion::getVersionNo));
        return pending.stream()
                .filter(p -> openGoals.contains(p.getGoalId()))
                .filter(p -> Objects.equals(currentNos.get(p.getGoalId()), p.getBasedOnVersionNo()))
                .map(ChangeProposal::getId)
                .collect(Collectors.toSet());
    }

    private ChangeProposalResponse toResponse(ChangeProposal proposal) {
        return changeProposalMapper.toResponse(proposal, applicable(List.of(proposal)).contains(proposal.getId()));
    }

    private void publishDecided(ChangeProposal proposal) {
        outbox.append(new OutboxMessage("ChangeProposal", proposal.getId(), proposal.getUserId(),
                ProposalDecidedV1.TYPE, ProposalDecidedV1.VERSION, changeProposalMapper.toDecidedEvent(proposal)));
    }

    private ChangeProposal load(UUID userId, UUID proposalId) {
        return repository.findByIdAndUserId(proposalId, userId)
                .orElseThrow(() -> new LifeOsException(CommonErrorCode.NOT_FOUND, "Proposal not found."));
    }
}
