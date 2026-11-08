package com.lifeos.strategy.knowledge.service.impl;

import com.lifeos.base.error.CommonErrorCode;
import com.lifeos.base.error.LifeOsException;
import com.lifeos.base.messaging.outbox.Outbox;
import com.lifeos.base.messaging.outbox.OutboxMessage;
import com.lifeos.strategy.common.error.StrategyErrorCode;
import com.lifeos.strategy.goal.entity.Goal;
import com.lifeos.strategy.goal.repository.GoalRepository;
import com.lifeos.strategy.knowledge.dto.CreateKnowledgeItemRequest;
import com.lifeos.strategy.knowledge.dto.KnowledgeItemResponse;
import com.lifeos.strategy.knowledge.dto.UpdateKnowledgeItemRequest;
import com.lifeos.strategy.knowledge.entity.KnowledgeItem;
import com.lifeos.strategy.knowledge.entity.KnowledgeKind;
import com.lifeos.strategy.knowledge.entity.KnowledgeStatus;
import com.lifeos.strategy.knowledge.event.KnowledgeItemChangedV1;
import com.lifeos.strategy.knowledge.mapper.KnowledgeItemMapper;
import com.lifeos.strategy.knowledge.repository.KnowledgeItemRepository;
import com.lifeos.strategy.knowledge.service.KnowledgeItemService;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Changes are flushed before the event is written, so the event carries the new version. */
@Service
public class KnowledgeItemServiceImpl implements KnowledgeItemService {

    private final KnowledgeItemRepository repository;
    private final GoalRepository goalRepository;
    private final KnowledgeItemMapper knowledgeItemMapper;
    private final Outbox outbox;
    private final Clock clock;

    public KnowledgeItemServiceImpl(KnowledgeItemRepository repository, GoalRepository goalRepository,
            KnowledgeItemMapper knowledgeItemMapper, Outbox outbox, Clock clock) {
        this.repository = repository;
        this.goalRepository = goalRepository;
        this.knowledgeItemMapper = knowledgeItemMapper;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    @Transactional
    public KnowledgeItemResponse create(UUID userId, UUID goalId, CreateKnowledgeItemRequest request) {
        requireOpen(loadGoal(userId, goalId));
        KnowledgeItem item = KnowledgeItem.create(goalId, userId, request.statement().strip(), request.kind(),
                request.status() != null ? request.status() : KnowledgeStatus.UNVERIFIED,
                Boolean.TRUE.equals(request.external()), strip(request.source()), strip(request.howToVerify()),
                clock.instant());
        repository.saveAndFlush(item);
        publishChanged(item);
        return knowledgeItemMapper.toResponse(item);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeItemResponse> list(UUID userId, UUID goalId, KnowledgeKind kind, KnowledgeStatus status) {
        loadGoal(userId, goalId);
        return repository.search(goalId, kind, status).stream().map(knowledgeItemMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public KnowledgeItemResponse get(UUID userId, UUID goalId, UUID itemId) {
        return knowledgeItemMapper.toResponse(loadItem(userId, goalId, itemId));
    }

    @Override
    @Transactional
    public KnowledgeItemResponse update(UUID userId, UUID goalId, UUID itemId, UpdateKnowledgeItemRequest request) {
        KnowledgeItem item = loadItem(userId, goalId, itemId);
        if (item.getVersion() != request.version()) {
            throw new LifeOsException(CommonErrorCode.CONCURRENT_MODIFICATION,
                    "The item was changed in the meantime. Reload it and try again.",
                    Map.of("currentVersion", item.getVersion()));
        }
        requireOpen(loadGoal(userId, goalId));
        boolean changed = item.update(strip(request.statement()), request.kind(), request.status(),
                request.external(), strip(request.source()), strip(request.howToVerify()), clock.instant());
        if (changed) {
            repository.saveAndFlush(item);
            publishChanged(item);
        }
        return knowledgeItemMapper.toResponse(item);
    }

    private Goal loadGoal(UUID userId, UUID goalId) {
        return goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new LifeOsException(CommonErrorCode.NOT_FOUND, "Goal not found."));
    }

    private KnowledgeItem loadItem(UUID userId, UUID goalId, UUID itemId) {
        return repository.findByIdAndGoalIdAndUserId(itemId, goalId, userId)
                .orElseThrow(() -> new LifeOsException(CommonErrorCode.NOT_FOUND, "Knowledge item not found."));
    }

    private static void requireOpen(Goal goal) {
        if (!goal.getStatus().isOpen()) {
            throw new LifeOsException(StrategyErrorCode.STRATEGY_GOAL_CLOSED,
                    "This goal is " + goal.getStatus().name().toLowerCase() + "; its knowledge map is read-only.");
        }
    }

    private void publishChanged(KnowledgeItem item) {
        outbox.append(new OutboxMessage("KnowledgeItem", item.getId(), item.getUserId(),
                KnowledgeItemChangedV1.TYPE, KnowledgeItemChangedV1.VERSION,
                knowledgeItemMapper.toChangedEvent(item)));
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
